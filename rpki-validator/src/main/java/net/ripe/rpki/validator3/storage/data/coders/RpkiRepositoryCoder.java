package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RpkiRepositoryCoder implements Coder<RpkiRepository> {

    private static final Tags tags = new Tags();
    private final static short TYPE_TAG = tags.unique(1);
    private final static short RRDP_NOTIFY_URL_TAG = tags.unique(2);
    private final static short RSYNC_URL_TAG = tags.unique(3);
    private final static short STATUS_TAG = tags.unique(4);
    private final static short RRDP_SERIAL = tags.unique(5);
    private final static short RRDP_SESSION = tags.unique(6);
    private final static short LAST_DOWNLOADED = tags.unique(7);
    private final static short PARENT_REPOSITORY = tags.unique(8);
    private final static short TRUST_ANCHORS = tags.unique(9);

    private final static RefCoder<RpkiRepository> repoRefCoder = new RefCoder<>();
    private final static RefCoder<TrustAnchor> taRefCoder = new RefCoder<>();

    @Override
    public byte[] toBytes(RpkiRepository rpkiRepository) {
        final Encoded encoded = new Encoded();

        BaseCoder.toBytes(rpkiRepository, encoded);

        encoded.append(TYPE_TAG, Coders.toBytes(rpkiRepository.getType().name()));
        encoded.append(STATUS_TAG, Coders.toBytes(rpkiRepository.getStatus().name()));
        encoded.appendNotNull(rpkiRepository.getRrdpNotifyUri(), RRDP_NOTIFY_URL_TAG, Coders::toBytes);
        encoded.appendNotNull(rpkiRepository.getRsyncRepositoryUri(), RSYNC_URL_TAG, Coders::toBytes);
        encoded.appendNotNull(rpkiRepository.getRrdpSessionId(), RRDP_SESSION, Coders::toBytes);
        encoded.appendNotNull(rpkiRepository.getRrdpSerial(), RRDP_SERIAL, Coders::toBytes);
        encoded.appendNotNull(rpkiRepository.getLastDownloadedAt(), LAST_DOWNLOADED, Coders::toBytes);
        encoded.appendNotNull(rpkiRepository.getParentRepository(), PARENT_REPOSITORY, repoRefCoder::toBytes);

        if (rpkiRepository.getTrustAnchors() != null && !rpkiRepository.getTrustAnchors().isEmpty()) {
            byte[] taBytes = Coders.toBytes(rpkiRepository.getTrustAnchors(), taRefCoder::toBytes);
            encoded.append(TRUST_ANCHORS, taBytes);
        }

        return encoded.toByteArray();
    }

    @Override
    public RpkiRepository fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();

        final RpkiRepository rpkiRepository = new RpkiRepository();
        BaseCoder.fromBytes(content, rpkiRepository);

        rpkiRepository.setType(RpkiRepository.Type.valueOf(Coders.toString(content.get(TYPE_TAG))));
        rpkiRepository.setStatus(Coders.toString(content.get(STATUS_TAG)));
        Encoded.field(content, RRDP_NOTIFY_URL_TAG).ifPresent(b -> rpkiRepository.setRrdpNotifyUri(Coders.toString(b)));
        Encoded.field(content, RSYNC_URL_TAG).ifPresent(b -> rpkiRepository.setRsyncRepositoryUri(Coders.toString(b)));
        Encoded.field(content, RRDP_SESSION).ifPresent(b -> rpkiRepository.setRrdpSessionId(Coders.toString(b)));
        Encoded.field(content, RRDP_SERIAL).ifPresent(b -> rpkiRepository.setRrdpSerial(Coders.toBigInteger(b)));
        Encoded.field(content, LAST_DOWNLOADED).ifPresent(b -> rpkiRepository.setLastDownloadedAt(Coders.toInstant(b)));
        Encoded.field(content, PARENT_REPOSITORY).ifPresent(b -> rpkiRepository.setParentRepository(repoRefCoder.fromBytes(b)));

        Encoded.field(content, TRUST_ANCHORS).ifPresent(b -> {
            final List<Ref<TrustAnchor>> objects = Coders.fromBytes(b, taRefCoder::fromBytes);
            rpkiRepository.setTrustAnchors(new HashSet<>(objects));
        });

        return rpkiRepository;
    }

}
