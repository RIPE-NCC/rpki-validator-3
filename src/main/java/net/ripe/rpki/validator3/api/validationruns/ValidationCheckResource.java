package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import lombok.Data;
import net.ripe.rpki.validator3.domain.ValidationCheck;

import java.util.List;

@Data(staticConstructor = "of")
@ApiModel(value = "ValidationCheck")
public class ValidationCheckResource {
    final String location;
    final ValidationCheck.Status status;
    final String key;
    final List<String> parameters;

    public static ValidationCheckResource of(ValidationCheck check) {
        return of(check.getLocation(), check.getStatus(), check.getKey(), check.getParameters());
    }
}
