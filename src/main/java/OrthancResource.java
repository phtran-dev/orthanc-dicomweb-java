/**
 * SPDX-FileCopyrightText: 2023-2024 Sebastien Jodogne, UCLouvain, Belgium
 * SPDX-License-Identifier: GPL-3.0-or-later
 **/

/**
 * Java plugin for Orthanc
 * Copyright (C) 2023-2024 Sebastien Jodogne, UCLouvain, Belgium
 * <p>
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 **/

import be.uclouvain.orthanc.Functions;
import be.uclouvain.orthanc.ResourceType;
import org.dcm4che3.data.Tag;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrthancResource {

    private final ResourceType type;

    private final String id;

    private final String lastUpdate;

    private final Map<String, String> tags;

    private List<String> children;

    public OrthancResource(JSONObject info) {
        String s = info.getString("Type");
        switch (s) {
            case "Patient":
                type = ResourceType.PATIENT;
                break;
            case "Study":
                type = ResourceType.STUDY;
                break;
            case "Series":
                type = ResourceType.SERIES;
                break;
            case "Instance":
                type = ResourceType.INSTANCE;
                break;
            default:
                throw new RuntimeException("Unknown resource type");
        }

        id = info.getString("ID");
        lastUpdate = info.optString("LastUpdate");
        tags = new HashMap<>();
        addToDictionary(tags, info.getJSONObject("MainDicomTags"));

        if (type != ResourceType.INSTANCE) {
            String childKey;
            switch (type) {
                case PATIENT:
                    childKey = "Studies";
                    break;
                case STUDY:
                    childKey = "Series";
                    addToDictionary(tags, info.getJSONObject("PatientMainDicomTags"));
                    break;
                case SERIES:
                    childKey = "Instances";
                    break;
                default:
                    throw new RuntimeException();
            }

            children = new ArrayList<>();
            addToListOfStrings(children, info.getJSONArray(childKey));
        }
    }

    static public void addToDictionary(Map<String, String> target,
                                       JSONObject source) {
        for (String key : source.keySet()) {
            Functions.logInfo("PHONG dic key=" + key);
            target.put(key, source.getString(key));
        }
    }

    static public void addToListOfStrings(List<String> target,
                                          JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            target.add(source.getString(i));
        }
    }

    public ResourceType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public List<String> getChildren() {
        if (type == ResourceType.INSTANCE) {
            throw new RuntimeException("A DICOM instance has no child");
        } else {
            return children;
        }
    }

    public static List<OrthancResource> find(ResourceType type,
                                              Map<String, String> tags,
                                              boolean caseSensitive,
                                              boolean hasPaging,
                                              int since,
                                              int limit) {
        JSONObject query = new JSONObject();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            query.put(entry.getKey(), entry.getValue());
        }

        JSONObject request = new JSONObject();
        request.put("Expand", true);
        request.put("Query", query);
        request.put("Short", false); // Hex or Human representation
        request.put("CaseSensitive", caseSensitive);

        if (hasPaging) {
            request.put("Since", since);
            request.put("Limit", limit);
        }

        switch (type) {
            case PATIENT:
                request.put("Level", "Patient");
                break;
            case STUDY:
                request.put("Level", "Study");
                break;
            case SERIES:
                request.put("Level", "Series");
                break;
            case INSTANCE:
                request.put("Level", "Instance");
                break;
            default:
                throw new RuntimeException();
        }

        byte[] response = Functions.restApiPost("/tools/find", request.toString().getBytes(StandardCharsets.UTF_8));

        JSONArray arr = new JSONArray(new String(response, StandardCharsets.UTF_8));

        List<OrthancResource> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            result.add(new OrthancResource(arr.getJSONObject(i)));
        }

        return result;
    }

}
