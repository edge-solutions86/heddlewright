package io.github.rkaushikdev.heddlewright.store;

import io.github.rkaushikdev.heddlewright.store.Journal.Entry;

/** Test support: edits a journal in place, the way a meddler would, for tests outside this package. */
public final class Tampering {

    private Tampering() {}

    /** Change the type of one entry, keeping the hash it was written with. */
    public static void retype(Journal journal, int index, String type) {
        Entry entry = journal.entryList().get(index);
        journal.entryList()
                .set(
                        index,
                        new Entry(entry.sequence(), type, entry.payload(), entry.at(), entry.previous(), entry.hash()));
    }
}
