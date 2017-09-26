package net.ripe.rpki.validator3.domain.constraints;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {})
@Size(min = ValidPublicKeyInfo.MIN, max = ValidPublicKeyInfo.MAX)
public @interface ValidPublicKeyInfo {
    int MIN = 100;
    int MAX = 2000;

    String message() default "{ValidPublicKeyInfo.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
