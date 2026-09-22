package com.feng.dsagent.presentation;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Addresses one rendered courseware page so that it can be stored by a cache.
 *
 * <p>A page image is the same bytes for every account and its id already carries the deck's content hash,
 * so the url for a page never needs to change. Two things stop it from being a plain path, though:
 *
 * <ul>
 *   <li>Page ids are sequence numbers inside a guessable deck id, so an unsigned url would let anyone walk
 *       the whole courseware. The signature is a keyed digest of the page id plus a render generation,
 *       which turns the url into a capability: unguessable, and stable for as long as both the page and
 *       its rendered bytes exist - bumping the generation is the one sanctioned way to re-point every
 *       cache at re-rendered bytes.
 *   <li>A cache decides whether an object is static from its path. A path without a file extension is
 *       treated as an API call and forwarded to the origin on every request - which is exactly the round
 *       trip per page turn this class exists to remove. Carrying the extension is therefore part of the
 *       contract, not decoration, and it is what lets the same url work unchanged behind today's edge and
 *       behind a mainland CDN or an object store in front of the app later on.
 * </ul>
 *
 * <p>The session cookie deliberately plays no part here: a cache filling itself has no cookie, so a url
 * that needed one could never be shared between users.
 */
@Component
public class SlideImageLinks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlideImageLinks.class);

    /** Shape of a signed page url; the two extensions it may carry. */
    public static final String WEBP = "webp";
    public static final String PNG = "png";

    private static final String PREFIX = "/api/v1/presentation/slides/";
    private static final String PURPOSE = "courseware-page:";
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Render generation of the page images. It is part of the signed input, so bumping it issues
     * every page a fresh url and lets immutable edge caches pick up the new bytes instead of
     * serving a stale render for a year. Bump it whenever the rendered bytes change in place
     * (v2: pages re-rendered at the decks' true 4:3 ratio after they shipped stretched to 16:9).
     */
    private static final String IMAGE_GENERATION = "v2";

    /** 128 bits of the digest: far beyond guessing, and short enough to keep the url readable. */
    private static final int SIGNATURE_BYTES = 16;

    private final byte[] key;

    public SlideImageLinks(PresentationProperties properties) {
        String secret = properties.assetSecret();
        if (secret == null || secret.isBlank()) {
            // Without a configured secret the choice is between signing with something public, which would
            // let anyone walk the courseware, and refusing to serve a page at all. A key that only this
            // process knows is neither: urls stay unguessable, at the cost of not surviving a restart.
            LOGGER.error("app.presentation.asset-secret is not configured: courseware page urls are signed "
                + "with a key that only this process knows, so every issued url stops working when it restarts");
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.key = ephemeral;
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Stable, signed, extension-bearing url for one page. */
    public String url(String slideId, String extension) {
        return PREFIX + slideId + "/" + signature(slideId) + "." + extension;
    }

    /** True when `candidate` is the signature this deployment issues for `slideId`. */
    public boolean verify(String slideId, String candidate) {
        if (slideId == null || slideId.isBlank() || candidate == null || candidate.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            signature(slideId).getBytes(StandardCharsets.UTF_8),
            candidate.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String signature(String slideId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal((PURPOSE + IMAGE_GENERATION + ":" + slideId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(ALGORITHM + " is not available", error);
        }
    }
}
