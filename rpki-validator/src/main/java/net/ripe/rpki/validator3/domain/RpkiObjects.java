package net.ripe.rpki.validator3.domain;

import fj.data.Either;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import org.apache.commons.lang3.tuple.Pair;

public class RpkiObjects {

    public static Either<ValidationResult, Pair<String, RpkiObject>> createRpkiObject(final String uri, final byte[] content) {
        ValidationResult validationResult = ValidationResult.withLocation(uri);
        CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
        if (validationResult.hasFailures()) {
            return Either.left(validationResult);
        } else {
            return Either.right(Pair.of(uri, new RpkiObject(repositoryObject)));
        }
    }
}
