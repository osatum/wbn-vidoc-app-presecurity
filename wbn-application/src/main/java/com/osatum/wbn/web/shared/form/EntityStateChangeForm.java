package com.osatum.wbn.web.shared.form;

import com.osatum.wbn.domain.model.EntityState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Formularz zmiany stanu użytkownika lub powiązanej encji")
public class EntityStateChangeForm {
    @NotNull
    @Schema(description = "Docelowy stan użytkownika lub encji.", requiredMode = Schema.RequiredMode.REQUIRED)
    private EntityState state;

    @Schema(description = "Identyfikatory powiązań użytkownika z instytucjami, które mają zostać aktywowane podczas aktywacji konta. Pole jest używane, gdy użytkownik ma kilka aktywnych powiązań do wyboru.")
    private List<Long> membershipIds;
}
