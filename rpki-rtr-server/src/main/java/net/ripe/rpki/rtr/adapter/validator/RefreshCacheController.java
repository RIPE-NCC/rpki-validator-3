package net.ripe.rpki.rtr.adapter.validator;

import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rtr.domain.RpkiCache;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class RefreshCacheController {
    private final RestTemplate restTemplate;

    @Value("${rpki.validator.validated.roas.uri}")
    private URI validatedRoasUri;

    @Autowired
    private RpkiCache cache;

    public RefreshCacheController(RestTemplateBuilder restTemplateBuilder) {
        log.info("RefreshCacheController loaded");
        this.restTemplate = restTemplateBuilder.build();
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)
    private void refreshCache() {
        log.info("fetching validated roa prefixes from {}", validatedRoasUri);
        ValidatedRoasResponse response = restTemplate.getForObject(validatedRoasUri, ValidatedRoasResponse.class);

        ValidatedRoas validatedRoas = response.getData();
        if (!validatedRoas.ready) {
            log.warn("validator {} not ready yet, will retry later", validatedRoasUri);
            return;
        }

        List<ValidatedPrefix> validatedPrefixes = validatedRoas.getRoas();
        log.info("fetched {} validated roa prefixes from {}", validatedPrefixes.size(), validatedRoasUri);

        List<Pdu> roaPrefixes = validatedPrefixes.stream().map(prefix -> Pdu.prefix(
            Asn.parse(prefix.getAsn()),
            IpRange.parse(prefix.getPrefix()),
            prefix.getMaxLength()
        )).distinct().collect(toList());

        cache.updateValidatedPdus(roaPrefixes);
    }

    @lombok.Value
    public static class ValidatedRoasResponse {
        public ValidatedRoas data;
    }

    @lombok.Value
    public static class ValidatedRoas {
        boolean ready;
        //        @ApiModelProperty(position = 2)
//        Collection<TrustAnchorResource> trustAnchors;
        @ApiModelProperty(position = 3)
        List<ValidatedPrefix> roas;
    }

    @lombok.Value
    static class ValidatedPrefix {
        String asn;
        Integer maxLength;
        String prefix;
    }
}
