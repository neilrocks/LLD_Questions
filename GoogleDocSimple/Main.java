package GoogleDocSimple;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
/**
 CLASSES:
User
Document
EditOperation
InsertOp
DeleteOp
DocStore
CollaborationManager
 */
class GoogleDoc {

    // Represents a user editing the document
    static class User {
        String id;
        String name;

        User(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // Represents a document
    static class Document {

        String id;

        // actual document text
        StringBuilder txt = new StringBuilder();

        // version number of document
        // incremented after every successful edit
        AtomicInteger ver = new AtomicInteger(0);

        // read-write lock
        // multiple readers allowed
        // only one writer allowed
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

        Document(String id, String initialText) {
            this.id = id;
            txt.append(initialText);
        }
    }

    // Storage layer for documents
    static class DocStore {

        // thread-safe storage
        Map<String, Document> docs = new ConcurrentHashMap<>();

        // create a new document
        Document create(String initialText) {

            String id = UUID.randomUUID().toString();

            Document d = new Document(id, initialText);

            docs.put(id, d);

            return d;
        }

        // fetch document
        Document get(String id) {
            return docs.get(id);
        }
    }

    // Base class representing an edit operation
    static abstract class EditOp {

        // document id
        String did;

        // user performing edit
        String uid;

        // version on which the edit was created
        int base;

        EditOp(String docId, String userId, int baseVersion) {
            this.did = docId;
            this.uid = userId;
            this.base = baseVersion;
        }

        // apply edit on document
        abstract void apply(Document d);
    }

    // Insert operation
    static class InsertOp extends EditOp {

        int pos;
        String txt;

        InsertOp(String d, String u, int b, int pos, String txt) {
            super(d, u, b);
            this.pos = pos;
            this.txt = txt;
        }

        void apply(Document d) {

            // ensure position does not exceed document length
            int p = Math.min(pos, d.txt.length());

            // insert text
            d.txt.insert(p, txt);
        }
    }

    // Delete operation
    static class DeleteOp extends EditOp {

        int pos;
        int len;

        DeleteOp(String d, String u, int b, int pos, int len) {
            super(d, u, b);
            this.pos = pos;
            this.len = len;
        }

        void apply(Document d) {

            int p = Math.min(pos, d.txt.length());

            int l = Math.min(len, d.txt.length() - p);

            if (l > 0) {
                d.txt.delete(p, p + l);
            }
        }
    }

    // Collaboration manager
    // responsible for applying edits safely
    static class CollabMgr {

        DocStore store;

        CollabMgr(DocStore store) {
            this.store = store;
        }

        // submit edit request
        int submit(EditOp op) throws Exception {

            Document d = store.get(op.did);

            if (d == null)
                throw new Exception("Document not found");

            // acquire write lock (only one editor allowed)
            d.rw.writeLock().lock();

            try {

                int currentVersion = d.ver.get();

                // version check
                // prevents editing stale document state
                if (op.base != currentVersion)
                    throw new Exception("Version mismatch");

                // apply edit
                op.apply(d);

                // increment version
                return d.ver.incrementAndGet();

            } finally {

                d.rw.writeLock().unlock();
            }
        }

        // read document
        String read(String docId) {

            Document d = store.get(docId);

            if (d == null)
                return null;

            // allow multiple readers
            d.rw.readLock().lock();

            try {

                return d.txt.toString();

            } finally {

                d.rw.readLock().unlock();
            }
        }
    }

    // Demo
    public static void main(String[] args) throws Exception {

        DocStore store = new DocStore();
        CollabMgr cm = new CollabMgr(store);

        User alice = new User("u1", "Alice");
        User bob = new User("u2", "Bob");

        Document doc = store.create("Hello World");

        // Alice inserts text
        int v1 = cm.submit(
                new InsertOp(doc.id, alice.id, 0, 5, ", A"));

        System.out.println("After Alice edit v=" + v1 + ": " + cm.read(doc.id));

        // Bob deletes text
        int v2 = cm.submit(
                new DeleteOp(doc.id, bob.id, v1, 0, 5));

        System.out.println("After Bob edit v=" + v2 + ": " + cm.read(doc.id));
    }
}