import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.BufferedImageUtils;
import org.dcm4che3.image.PixelAspectRatio;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.ws.rs.core.MediaType;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

public class RenderedImageOutput {

    private static final float DEF_FRAME_TIME = 1000.f;
    private static final byte[] LOOP_FOREVER = {1, 0, 0};

    private final ImageReader reader;

    private final DicomImageReadParam readParam;

    private final int rows;

    private final int columns;

    private final int imageIndex;

    private final ImageWriter writer;

    private final ImageWriteParam writeParam;

    private final byte[] baos;

    public RenderedImageOutput(byte[] baos,
                               int rows, int columns, MediaType mimeType, String imageQuality, int frame) {
        this.baos = baos;
        this.reader = getDicomImageReader();
        this.readParam = (DicomImageReadParam) reader.getDefaultReadParam();
        ;
        this.rows = rows;
        this.columns = columns;
        this.imageIndex = frame - 1;
        this.writer = getImageWriter(mimeType);
        this.writeParam = writer.getDefaultWriteParam();
        if (imageQuality != null) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(Integer.parseInt(imageQuality) / 100.f);
        }
    }

    public void write(OutputStream out) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(baos))) {
            reader.setInput(iis);
            ImageOutputStream imageOut = new MemoryCacheImageOutputStream(out);
            writer.setOutput(imageOut);
            BufferedImage bi = null;
            if (imageIndex < 0) {
                IIOMetadata metadata = null;
                int numImages = reader.getNumImages(false);
                writer.prepareWriteSequence(null);
                for (int i = 0; i < numImages; i++) {
                    readParam.setDestination(bi);
                    bi = reader.read(i, readParam);
                    BufferedImage bi2 = adjust(bi);
                    if (metadata == null)
                        metadata = createAnimatedGIFMetadata(bi2, writeParam, frameTime());
                    writer.writeToSequence(
                            new IIOImage(bi2, null, metadata),
                            writeParam);
                    imageOut.flush();
                }
                writer.endWriteSequence();
            } else {
                bi = reader.read(imageIndex, readParam);
                writer.write(null, new IIOImage(adjust(bi), null, null), writeParam);
            }
            imageOut.close();   // does not close out,
            // marks imageOut as closed to prevent finalizer thread to invoke out.flush()
        } finally {
            writer.dispose();
            reader.dispose();
        }
    }

    static BufferedImage rescale(BufferedImage bi, int r, int c, float sy) throws IOException {
        if (r == 0 && c == 0 && sy == 1f)
            return bi;

        float sx = 1f;
        if (r != 0 || c != 0) {
            if (r != 0 && c != 0)
                if (r * bi.getWidth() > c * bi.getHeight() * sy)
                    r = 0;
                else
                    c = 0;
            sx = r != 0 ? r / (bi.getHeight() * sy) : c / (float) bi.getWidth();
            sy *= sx;
        }
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(sx, sy),
                AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(bi, null);
    }

    private float frameTime() throws IOException {
        DicomMetaData metaData = (DicomMetaData) reader.getStreamMetadata();
        Attributes attrs = metaData.getAttributes();
        return attrs.getFloat(Tag.FrameTime, DEF_FRAME_TIME);
    }

    private IIOMetadata createAnimatedGIFMetadata(BufferedImage bi, ImageWriteParam param, float frameTime)
            throws IOException {
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromRenderedImage(bi);
        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, param);
        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
        IIOMetadataNode graphicControlExt =
                (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
        graphicControlExt.setAttribute("delayTime", Integer.toString(Math.round(frameTime() / 10)));
        IIOMetadataNode appExts = new IIOMetadataNode("ApplicationExtensions");
        IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
        appExt.setAttribute("applicationID", "NETSCAPE");
        appExt.setAttribute("authenticationCode", "2.0");
        appExt.setUserObject(LOOP_FOREVER);
        appExts.appendChild(appExt);
        root.appendChild(appExts);
        metadata.setFromTree(formatName, root);
        return metadata;
    }

    private BufferedImage adjust(BufferedImage bi) throws IOException {
        if (bi.getColorModel().getNumComponents() == 3)
            bi = BufferedImageUtils.convertToIntRGB(bi);
        return rescale(bi, rows, columns, getPixelAspectRatio());
    }

    private float getPixelAspectRatio() throws IOException {
        Attributes prAttrs = readParam.getPresentationState();
        return prAttrs != null ? PixelAspectRatio.forPresentationState(prAttrs)
                : PixelAspectRatio.forImage(getAttributes());
    }

    private Attributes getAttributes() throws IOException {
        return ((DicomMetaData) reader.getStreamMetadata()).getAttributes();
    }

    private static ImageReader getDicomImageReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            ImageIO.scanForPlugins();
            readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext())
                throw new RuntimeException("DICOM Image Reader not registered");
        }
        return readers.next();
    }

    private ImageWriter getImageWriter(MediaType mimeType) {
        String formatName = formatNameOf(mimeType);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext())
            throw new RuntimeException(formatName + " Image Writer not registered");

        return writers.next();
    }

    private String formatNameOf(MediaType mimeType) {
        return mimeType.getSubtype().toUpperCase();
    }

}