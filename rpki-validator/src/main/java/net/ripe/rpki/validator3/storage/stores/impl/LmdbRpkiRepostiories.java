package net.ripe.rpki.validator3.storage.stores.impl;

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.stores.RpkiRepostioryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class LmdbRpkiRepostiories implements RpkiRepostioryStore {

    private static final String RPKI_REPOSITORIES = "rpki-repositories";

    private final IxMap<RpkiRepository> ixMap;

    @Autowired
    public LmdbRpkiRepostiories(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                RPKI_REPOSITORIES,
                new FSTCoder<>(),
                ImmutableMap.of());
    }

    @Override
    public RpkiRepository register(TrustAnchor trustAnchor, String uri, RpkiRepository.Type type) {
        return null;
    }

    @Override
    public Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri) {
        return Optional.empty();
    }

    @Override
    public RpkiRepository get(long id) {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm, Sorting sorting, Paging paging) {
        return null;
    }

    @Override
    public long countAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        return 0;
    }

    @Override
    public Map<RpkiRepository.Status, Long> countByStatus(Long taId, boolean hideChildrenOfDownloadedParent) {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findRsyncRepositories() {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findRrdpRepositories() {
        return null;
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {

    }

    @Override
    public void remove(long id) {

    }
}
