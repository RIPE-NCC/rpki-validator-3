package net.ripe.rpki.validator3.api.trustanchors;

import com.google.common.io.PatternFilenameFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Profile("!test")
@Slf4j
public class PreconfiguredTrustAnchors {
    @Autowired
    private TrustAnchors trustAnchors;
    @Autowired
    private TrustAnchorService trustAnchorService;

    @Value("${rpki.validator.preconfigured.trust.anchors.directory}")
    private File preconfiguredTrustAnchorDirectory;

    @PostConstruct
    public void managePreconfiguredTrustAnchors() {
        log.info("Automatically adding preconfigured trust anchors");

        File[] tals = preconfiguredTrustAnchorDirectory.listFiles(new PatternFilenameFilter(Pattern.compile("^.*\\.tal$")));
        if (ArrayUtils.isEmpty(tals)) {
            log.warn("No preconfigured trust anchors found, skipping");
            return;
        }

        for (File tal : tals) {
            TrustAnchorLocator locator = TrustAnchorLocator.fromFile(tal);
            if (trustAnchors.findBySubjectPublicKeyInfo(locator.getPublicKeyInfo()).isPresent()) {
                log.info("Preconfigured trust anchor '{}' already installed, skipping", locator.getCaName());
                continue;
            }

            AddTrustAnchor command = AddTrustAnchor.builder()
                .type(TrustAnchor.TYPE)
                .name(locator.getCaName())
                .locations(locator.getCertificateLocations().stream().map(URI::toASCIIString).collect(Collectors.toList()))
                .subjectPublicKeyInfo(locator.getPublicKeyInfo())
                .rsyncPrefetchUri(locator.getPrefetchUris().stream()
                    .filter(uri -> "rsync".equalsIgnoreCase(uri.getScheme()))
                    .map(URI::toASCIIString)
                    .findFirst().orElse(null)
                )
                .build();
            trustAnchorService.execute(command);
        }
    }
}
