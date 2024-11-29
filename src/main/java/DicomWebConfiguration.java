import be.uclouvain.orthanc.Functions;

import org.json.JSONObject;

public class DicomWebConfiguration {

    private static final DicomWebConfiguration INSTANCE = new DicomWebConfiguration();

    static private final String KEY_DICOM_WEB = "DicomWeb";
    static private final String KEY_ROOT = "Root";

    private String dicomWebRoot = "/wado-url/";


    private DicomWebConfiguration() {
        JSONObject configuration = new JSONObject(Functions.getConfiguration());

        if (configuration.has(KEY_DICOM_WEB)) {
            JSONObject dicomWeb = configuration.getJSONObject(KEY_DICOM_WEB);
            if (dicomWeb.has(KEY_ROOT)) {
                dicomWebRoot = dicomWeb.getString(KEY_ROOT);
            }
        }
    }

    public String getDicomWebRoot() {
        return this.dicomWebRoot;
    }

    static public DicomWebConfiguration getInstance() {
        return INSTANCE;
    }

}