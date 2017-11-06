package net.ripe.rpki.validator3.api.roas;

import au.com.bytecode.opencsv.CSVWriter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * Controller to export validated ROA prefix information.
 * <p>
 * The data format is backwards compatible with the RPKI validator 2.x (see
 * https://github.com/RIPE-NCC/rpki-validator/blob/350d939d5e18858ee6cefc0c9a99e0c70b609b6d/rpki-validator-app/src/main/scala/net/ripe/rpki/validator/controllers/ExportController.scala#L41).
 */
@RestController
@Slf4j
public class ExportsController {

    private final RpkiObjects rpkiObjects;

    @Autowired
    public ExportsController(RpkiObjects rpkiObjects) {
        this.rpkiObjects = rpkiObjects;
    }

    @GetMapping(path = "/export.json", produces = "text/json; charset=UTF-8")
    public JsonExport exportJson() {
        return new JsonExport(loadValidatedPrefixes());
    }

    @GetMapping(path = "/export.csv", produces = "text/csv; charset=UTF-8")
    public void exportCsv(HttpServletResponse response) throws IOException {
        Stream<ExportRoaPrefix> validatedPrefixes = loadValidatedPrefixes();

        response.setContentType("text/csv; charset=UTF-8");

        try (CSVWriter writer = new CSVWriter(response.getWriter())) {
            writer.writeNext(new String[]{"ASN", "IP Prefix", "Max Length", "Trust Anchor"});
            validatedPrefixes.forEach(prefix -> {
                writer.writeNext(new String[]{prefix.getAsn(), prefix.getPrefix(), String.valueOf(prefix.getMaxLength()), prefix.getTa()});
            });
        }
    }

    protected Stream<ExportRoaPrefix> loadValidatedPrefixes() {
        return rpkiObjects
            .findCurrentlyValidated(RpkiObject.Type.ROA)
            .flatMap(pair -> pair.getValue().getRoaPrefixes().stream()
                .map(prefix -> new ExportRoaPrefix(
                    String.valueOf(prefix.getAsn()),
                    prefix.getPrefix(),
                    prefix.getEffectiveLength(),
                    pair.getKey().getTrustAnchor().getName()
                ))
            );
    }

    @Value
    public static class JsonExport {
        Stream<ExportRoaPrefix> roa;
    }

    @Value
    public static class ExportRoaPrefix {
        private String asn;
        private String prefix;
        private int maxLength;
        private String ta;
    }
}
