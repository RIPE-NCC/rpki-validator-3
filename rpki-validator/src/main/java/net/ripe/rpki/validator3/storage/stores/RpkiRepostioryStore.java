package net.ripe.rpki.validator3.storage.stores;

import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.springframework.web.bind.annotation.PathVariable;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public interface RpkiRepostioryStore {

    RpkiRepository register(TrustAnchor trustAnchor, String uri, RpkiRepository.Type type);

    Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri);

    RpkiRepository get(long id);

    Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent,
                                   SearchTerm searchTerm, Sorting sorting, Paging paging);

    long countAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm);

    Map<RpkiRepository.Status, Long> countByStatus(@PathVariable Long taId, boolean hideChildrenOfDownloadedParent);

    default Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId) {
        return findAll(optionalStatus, taId, false, null, null, null);
    }

    default Stream<RpkiRepository> findAll(Long taId) {
        return findAll(null, taId, false, null, null, null);
    }

    Stream<RpkiRepository> findRsyncRepositories();

    Stream<RpkiRepository> findRrdpRepositories();

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);

    void remove(long id);
}
