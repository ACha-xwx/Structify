package com.feng.dsagent.compiler;

/**
 * One class listing that ships with the app: the source as printed, without a {@code main} of its own.
 *
 * <p>Because such a listing cannot run on its own, the library also carries a complete runnable
 * example around it ({@link #example()}). When even that is not possible - the listing is pseudocode,
 * or leans on APIs that only exist on one platform - {@link #blocked()} says why, in one sentence, so
 * the editor can be honest instead of offering a button that cannot work.
 */
public record TextbookCodeFragment(
    String id,
    String file,
    String title,
    String kind,
    String startWith,
    String code,
    TextbookCodeExample example,
    String blocked
) {

    public TextbookCodeFragment {
        kind = kind == null || kind.isBlank() ? "algorithm" : kind;
        startWith = startWith == null || startWith.isBlank() ? "main" : startWith;
        code = code == null ? "" : code;
        blocked = blocked == null ? "" : blocked;
    }
}
