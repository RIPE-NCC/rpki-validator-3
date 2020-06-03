package net.ripe.rpki.validator3.config;

import lombok.Getter;
import net.ripe.rpki.commons.validation.ValidationOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidationConfig {
    @Value("${rpki.validator.strict-validation:false}")
    @Getter
    private boolean strictValidation;

    @Value("${rpki.validator.rsync-only:false}")
    @Getter
    private boolean rsyncOnly;

    public ValidationOptions validationOptions(){
        if(strictValidation){
            return ValidationOptions.strictValidations();
        } else
            return ValidationOptions.defaultRipeNccValidator();
    }
}
