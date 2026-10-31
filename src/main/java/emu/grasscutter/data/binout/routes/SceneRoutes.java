package emu.grasscutter.data.binout.routes;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SceneRoutes {
    @SerializedName(
            value = "sceneId",
            alternate = {"AEJNKFGDCEK", "BHMDDFNMFAB"})
    private int sceneId;

    @SerializedName(
            value = "routes",
            alternate = {"LOLKGILMKJN", "CNOMPALHFAC"})
    @Nullable
    private Route[] routes;
}
