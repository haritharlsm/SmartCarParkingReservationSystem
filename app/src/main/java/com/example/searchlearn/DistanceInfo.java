package com.example.searchlearn;

public class DistanceInfo {
    private String spaceName;
    private String distance;
    private String spaceId;

    public DistanceInfo(String spaceName, String distance, String spaceId) {
        this.spaceName = spaceName;
        this.distance = distance;
        this.spaceId = spaceId;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public String getDistance() {
        return distance;
    }

    public String getSpaceId() {
        return spaceId;
    }
}
