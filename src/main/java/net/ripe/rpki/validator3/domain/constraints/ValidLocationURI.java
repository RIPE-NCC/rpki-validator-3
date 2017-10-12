package net.ripe.rpki.validator3.domain.constraints;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.Pattern;
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
@Size(min = ValidLocationURI.MIN, max = ValidLocationURI.MAX)
@Pattern(regexp = "^rsync://.+$", flags = Pattern.Flag.CASE_INSENSITIVE)
public @interface ValidLocationURI {
    int MIN = 5;
    int MAX = 160000;

    String message() default "{net.ripe.rpki.validator3.domain.constraints.ValidLocationURI.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
