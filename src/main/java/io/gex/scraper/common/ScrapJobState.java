package io.gex.scraper.common;

import com.google.gson.annotations.SerializedName;

public enum ScrapJobState {
    @SerializedName("created")
    CREATED,
    @SerializedName("running")
    RUNNING,
    @SerializedName("finished")
    FINISHED,
    @SerializedName("error")
    ERROR,
    @SerializedName("stopped")
    STOPPED,
    @SerializedName("stopping")
    STOPPING,
    @SerializedName("interrupted")
    INTERRUPTED,
}
