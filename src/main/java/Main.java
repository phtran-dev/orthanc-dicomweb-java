import be.uclouvain.orthanc.*;

import java.io.ByteArrayOutputStream;

import org.dcm4che3.data.UID;
import org.dcm4che3.ws.rs.MediaTypes;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class Main {

    static {
        Callbacks.register(DicomWebConfiguration.getInstance().getDicomWebRoot() + "studies/(.*)/series/(.*)/thumbnail", (output, method, uri, regularExpressionGroups, headers, getParameters, body) -> {

            if (method != HttpMethod.GET) {
                output.sendMethodNotAllowed("GET");  // Answer with HTTP status 405
                return;
            }

            String seriesIUID = regularExpressionGroups[1];

            Viewport vp = new Viewport(getParameters.getOrDefault("viewport", "128,128"));

            List<OrthancResource> seriesResources = OrthancResource.find(ResourceType.SERIES, Collections.singletonMap("SeriesInstanceUID", seriesIUID), true, true, 0, 1);
            if (seriesResources.isEmpty()) {
                output.sendHttpStatus((short) 404, String.format("Cannot find SeriesInstanceUID: %s", seriesIUID).getBytes());
            }

            String modality = seriesResources.get(0).getTags().get("Modality");
            if (modality.equalsIgnoreCase("SR")) {
                output.answerBuffer(SRImage.image, MediaTypes.IMAGE_JPEG);
                return;
            }

            List<String> instanceResourceIds = seriesResources.get(0).getChildren();



            String instanceResourceId = instanceResourceIds.get(instanceResourceIds.size() / 2);

            boolean transcoded;
            {
                String transferSyntax = new String(Functions.restApiGet("/instances/" + instanceResourceId + "/metadata/TransferSyntax"));
                Functions.logInfo("PHONG transferSyntax=" + transferSyntax);
                transcoded = !(UID.ExplicitVRLittleEndian.equals(transferSyntax) || UID.ImplicitVRLittleEndian.equals(transferSyntax));
            }

            JSONObject request = new JSONObject();
            request.put("Transcode", UID.ExplicitVRLittleEndian);

            byte[] dicom = transcoded ? Functions.restApiPost("/instances/" + instanceResourceId + "/modify", request.toString().getBytes(StandardCharsets.UTF_8)) :
                Functions.restApiGet("/instances/" + instanceResourceId + "/file");
            RenderedImageOutput rio = new RenderedImageOutput(dicom, vp.getRows(), vp.getColumns(), MediaTypes.IMAGE_JPEG_TYPE, "90", 1);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                rio.write(out);
                output.answerBuffer(out.toByteArray(), MediaTypes.IMAGE_JPEG);
            } catch (Exception e) {
                Functions.logInfo(e.getMessage());
            }
        });
    }
}
