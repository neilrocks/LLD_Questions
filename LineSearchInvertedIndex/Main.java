/*
 Before the dry run, understand the two core structures:
lineStore — a simple dictionary of line number → text:
1 → "Delhi is the capital of India and has good food."
2 → "Mumbai is the financial capital of India."
3 → "Bangalore has good food and a great tech scene."
invertedIndex — for every unique word, which lines contain it:
"delhi"     → {1}
"good"      → {1, 3}
"food"      → {1, 3}
"capital"   → {1, 2}
"india"     → {1, 2}
"has"       → {1, 3}
"mumbai"    → {2}
...
The insight: instead of scanning all lines for every search, you look up words directly and find their lines in O(1), then intersect the sets.

Dry Run — loadDocument
Input:
"Delhi is the capital of India and has good food.\n
 Mumbai is the financial capital of India.\n
 Bangalore has good food and a great tech scene."
Split on \n → 3 lines. Process each:
Line 1 (lineNum = 1): "Delhi is the capital of India and has good food."

tokenize → ["delhi", "is", "the", "capital", "of", "india", "and", "has", "good", "food"]
For each word, add 1 to its posting set in invertedIndex

Line 2 (lineNum = 2): "Mumbai is the financial capital of India."

tokenize → ["mumbai", "is", "the", "financial", "capital", "of", "india"]
Add 2 to posting sets for each word. "capital" already has {1}, becomes {1,2}

Line 3 (lineNum = 3): "Bangalore has good food and a great tech scene."

tokenize → ["bangalore", "has", "good", "food", "and", "a", "great", "tech", "scene"]
"good" was {1}, becomes {1,3}. Same for "food" and "has"

Final index (key entries):
"good"    → {1, 3}
"food"    → {1, 3}
"delhi"   → {1}
"capital" → {1, 2}
"has"     → {1, 3}
"mumbai"  → {2}

Dry Run — search("Delhi has good food")
tokenize query → ["delhi", "has", "good", "food"]
Step 1: First word = "delhi" → candidates = {1} (copy of delhi's posting set)
Step 2: Intersect with remaining words one by one:
word = "has"   → posting = {1, 3}
candidates = {1} ∩ {1, 3} = {1}   ✓ still has candidates

word = "good"  → posting = {1, 3}
candidates = {1} ∩ {1, 3} = {1}   ✓

word = "food"  → posting = {1, 3}
candidates = {1} ∩ {1, 3} = {1}   ✓
Step 3: candidates = {1} → sort → [1] → look up in lineStore → return:
[Line 1] Delhi is the capital of India and has good food.
Notice: the query was "Delhi has good food" — words not adjacent, different order from the line — but it still matched. That's the power of the inverted index.

Dry Run — search("good food")
tokenize → ["good", "food"]
Step 1: first word = "good" → candidates = {1, 3}

Step 2:
  word = "food" → posting = {1, 3}
  candidates = {1, 3} ∩ {1, 3} = {1, 3}
Sort → [1, 3] → return:
[Line 1] Delhi is the capital of India and has good food.
[Line 3] Bangalore has good food and a great tech scene.

Dry Run — deleteLine(1)
Line 1 = "Delhi is the capital of India and has good food."
tokenize → ["delhi", "is", "the", "capital", "of", "india", "and", "has", "good", "food"]
For each word, remove 1 from its posting set:
"delhi"   → {1} remove 1 → {}  → empty, so delete key entirely
"is"      → {1, 2} remove 1 → {2}
"capital" → {1, 2} remove 1 → {2}
"good"    → {1, 3} remove 1 → {3}
"food"    → {1, 3} remove 1 → {3}
...
Also remove from lineStore: key 1 is deleted.
Now search "good food" again:
"good" → {3}
"food" → {3}
candidates = {3} ∩ {3} = {3}
Returns only:
[Line 3] Bangalore has good food and a great tech scene.
Line 1 is completely gone — from both the data store and the index.

The Core Mental Model
loadDocument  →  builds lineStore + invertedIndex
search        →  lookup each word → intersect line sets → fetch text
deleteLine    →  remove from lineStore + clean up index entries
Everything else is just bookkeeping around these three ideas.
 */

import java.util.*;

// ─── SearchResult ────────────────────────────────────────────────────────────

class SearchResult {
    int lineNumber;
    String lineContent;

    SearchResult(int lineNumber, String lineContent) {
        this.lineNumber = lineNumber;
        this.lineContent = lineContent;
    }

    public String toString() {
        return "[Line " + lineNumber + "] " + lineContent;
    }
}

// ─── DocumentSearchService ───────────────────────────────────────────────────

class DocumentSearchService {

    // lineNumber -> actual line text
    Map<Integer, String> lineStore = new LinkedHashMap<>();

    // word -> set of line numbers that contain this word
    Map<String, Set<Integer>> invertedIndex = new HashMap<>();

    // ── Load ─────────────────────────────────────────────────────────────────
    //Time O(W) — W = total words Space=O(W) for index
    void loadDocument(String text) {
        lineStore.clear();
        invertedIndex.clear();

        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            int lineNum = i + 1;
            lineStore.put(lineNum, line);

            // index every word in this line
            String[] words = tokenize(line);
            for (String word : words) {
                if (!invertedIndex.containsKey(word)) {
                    invertedIndex.put(word, new HashSet<>());
                }
                invertedIndex.get(word).add(lineNum);
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────
    //TIME: O(Q + R log R) — Q query words, SPACE: R matching linesO(R) output
    List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;

        String[] queryWords = tokenize(query);

        // Step 1: start with line numbers for the first query word
        String firstWord = queryWords[0];
        if (!invertedIndex.containsKey(firstWord)) return results; // no match at all

        Set<Integer> candidates = new HashSet<>(invertedIndex.get(firstWord));

        // Step 2: intersect with each remaining query word's line set
        for (int i = 1; i < queryWords.length; i++) {
            String word = queryWords[i];
            Set<Integer> posting = invertedIndex.getOrDefault(word, new HashSet<>());
            candidates.retainAll(posting); // keep only common line numbers
            if (candidates.isEmpty()) return results; // short-circuit
        }

        // Step 3: collect results in sorted order
        List<Integer> sortedLines = new ArrayList<>(candidates);
        Collections.sort(sortedLines);

        for (int lineNum : sortedLines) {
            results.add(new SearchResult(lineNum, lineStore.get(lineNum)));
        }

        return results;
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    //TIME: O(W_L) — W_L = words in that line SPACE: O(1) extra
    boolean deleteLine(int lineNumber) {
        if (!lineStore.containsKey(lineNumber)) return false;

        String line = lineStore.remove(lineNumber);

        // remove this line number from every word's posting set
        String[] words = tokenize(line);
        for (String word : words) {
            Set<Integer> posting = invertedIndex.get(word);
            if (posting != null) {
                posting.remove(lineNumber);
                if (posting.isEmpty()) {
                    invertedIndex.remove(word); // clean up empty entries
                }
            }
        }
        return true;
    }

    // ── Tokenize ──────────────────────────────────────────────────────────────

    String[] tokenize(String text) {
        // lowercase, strip punctuation, split on whitespace
        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9 ]", "");
        return cleaned.trim().split("\\s+");
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

class Main {
    public static void main(String[] args) {
        DocumentSearchService svc = new DocumentSearchService();

        String doc = "Delhi is the capital of India and has good food.\n"
                   + "Mumbai is the financial capital of India.\n"
                   + "Bangalore has good food and a great tech scene.";

        svc.loadDocument(doc);

        System.out.println("Search: 'Delhi has good food'");
        List<SearchResult> r1 = svc.search("Delhi has good food");
        for (SearchResult r : r1) System.out.println(r);

        System.out.println("\nSearch: 'good food'");
        List<SearchResult> r2 = svc.search("good food");
        for (SearchResult r : r2) System.out.println(r);

        System.out.println("\nDeleting line 1...");
        svc.deleteLine(1);

        System.out.println("Search 'good food' after delete:");
        List<SearchResult> r3 = svc.search("good food");
        for (SearchResult r : r3) System.out.println(r);
    }
}