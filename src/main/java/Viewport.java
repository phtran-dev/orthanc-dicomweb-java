import org.dcm4che3.util.StringUtils;

import java.awt.*;

public class Viewport {
    private final int rows;

    private final int columns;

    private final float[] region;

    public Viewport(String s) {
        String[] ss = StringUtils.split(s, ',');
        switch(ss.length) {
            case 2:
                region = null;
                break;
            case 6:
                region = new float[]{0, 0, Float.NaN, Float.NaN};
                for (int i = 2; i < 6; i++) {
                    if (!ss[i].isEmpty())
                        region[i-2] = Float.parseFloat(ss[i]);
                }
            default:
                throw new IllegalArgumentException(s);
        }
        columns = Integer.parseUnsignedInt(ss[0]);
        rows = Integer.parseUnsignedInt(ss[1]);
    }

    public Rectangle getSourceRegion(int rows, int columns) {
        if (region == null)
            return null;

        Rectangle result = new Rectangle();
        result.x = (int) Math.abs(region[0]);
        result.y = (int) Math.abs(region[1]);
        result.width = Float.isNaN(region[2])
                ? columns - result.x
                : (int) Math.abs(region[2]);
        result.height = Float.isNaN(region[3])
                ? rows - result.y
                : (int) Math.abs(region[3]);
        return result;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }
}
