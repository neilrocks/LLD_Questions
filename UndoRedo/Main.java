import java.util.*;

// Command interface representing an executable action
// that supports both execution and undo.
interface Command {
    void execute();
    void undo();
}

// Receiver: Contains the actual business logic of the editor.
// Commands delegate real work to this class.
class TextEditor {
    private StringBuilder text = new StringBuilder();

    // Inserts text at a given position
    public void insert(int pos, String str) {
        text.insert(pos, str);
    }

    // Deletes a substring starting from pos with length len
    public void delete(int pos, int len) {
        text.delete(pos, pos + len);
    }

    // Returns current editor content
    public String getText() {
        return text.toString();
    }
}

// Concrete Command for insert operation
class InsertCommand implements Command {

    private TextEditor editor;  // receiver
    private int position;       // position where text will be inserted
    private String text;        // text to insert

    public InsertCommand(TextEditor editor, int position, String text) {
        this.editor = editor;
        this.position = position;
        this.text = text;
    }

    // Executes the insert operation
    public void execute() {
        editor.insert(position, text);
    }

    // Undo insert by deleting the inserted text
    public void undo() {
        editor.delete(position, text.length());
    }
}

// Concrete Command for delete operation
class DeleteCommand implements Command {

    private TextEditor editor;  
    private int position;       
    private int length;         
    private String deletedText; // stored for undo operation

    public DeleteCommand(TextEditor editor, int position, int length) {
        this.editor = editor;
        this.position = position;
        this.length = length;
    }

    // Executes delete and stores removed text
    // so it can be restored during undo
    public void execute() {
        deletedText = editor.getText().substring(position, position + length);
        editor.delete(position, length);
    }

    // Undo delete by inserting the removed text back
    public void undo() {
        editor.insert(position, deletedText);
    }
}

// Invoker: Responsible for executing commands and
// managing undo/redo history.
class CommandManager {

    // Stack storing executed commands for undo
    private Stack<Command> undoStack = new Stack<>();

    // Stack storing undone commands for redo
    private Stack<Command> redoStack = new Stack<>();

    // Executes a command and records it in undo history
    public void executeCommand(Command cmd) {
        cmd.execute();
        undoStack.push(cmd);

        // Once a new command is executed,
        // redo history becomes invalid
        redoStack.clear();
    }

    // Undo last executed command
    public void undo() {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
    }

    // Redo last undone command
    public void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
        }
    }
}

// Client: Creates commands and passes them to the invoker
public class Main {

    public static void main(String[] args) {

        TextEditor editor = new TextEditor();     // receiver
        CommandManager manager = new CommandManager(); // invoker

        // Client creates commands and sends them to the invoker
        manager.executeCommand(new InsertCommand(editor, 0, "Hello"));
        manager.executeCommand(new InsertCommand(editor, 5, " World"));

        System.out.println(editor.getText()); // Hello World

        manager.undo();
        System.out.println(editor.getText()); // Hello

        manager.redo();
        System.out.println(editor.getText()); // Hello World
    }
}
// *******************************************************************

/** IF DELTA EDIT FOR MEMORY EFFICIENCY THEN BELOW CODE IS USED*/

//******************************************************************** 
import java.util.*;

// Command interface
interface Command {
    void execute();
    void undo();
}

// Receiver: Actual text editor
class TextEditor {

    private StringBuilder text = new StringBuilder();

    public void insert(int pos, String str) {
        text.insert(pos, str);
    }

    public void delete(int pos, int len) {
        text.delete(pos, pos + len);
    }

    public String substring(int pos, int len) {
        return text.substring(pos, pos + len);
    }

    public String getText() {
        return text.toString();
    }
}

// Represents the delta change between two states
class Edit {

    int position;
    String insertedText;
    String deletedText;

    public Edit(int position, String insertedText, String deletedText) {
        this.position = position;
        this.insertedText = insertedText;
        this.deletedText = deletedText;
    }
}

// Command storing only the edit delta
class EditCommand implements Command {

    private TextEditor editor;
    private Edit edit;

    public EditCommand(TextEditor editor, Edit edit) {
        this.editor = editor;
        this.edit = edit;
    }

    // Apply the edit
    public void execute() {

        if (edit.deletedText != null) {
            editor.delete(edit.position, edit.deletedText.length());
        }

        if (edit.insertedText != null) {
            editor.insert(edit.position, edit.insertedText);
        }
    }

    // Reverse the edit
    public void undo() {

        if (edit.insertedText != null) {
            editor.delete(edit.position, edit.insertedText.length());
        }

        if (edit.deletedText != null) {
            editor.insert(edit.position, edit.deletedText);
        }
    }
}

// Invoker: manages command history
class CommandManager {

    private Deque<Command> undoStack = new ArrayDeque<>();
    private Deque<Command> redoStack = new ArrayDeque<>();

    public void executeCommand(Command cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();
    }

    public void undo() {

        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
    }

    public void redo() {

        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
        }
    }
}

public class Main {

    public static void main(String[] args) {

        TextEditor editor = new TextEditor();
        CommandManager manager = new CommandManager();

        // Insert "Hello"
        manager.executeCommand(
                new EditCommand(editor, new Edit(0, "Hello", null)));

        // Insert " World"
        manager.executeCommand(
                new EditCommand(editor, new Edit(5, " World", null)));

        System.out.println(editor.getText()); // Hello World

        // Delete " World"
        String deleted = editor.substring(5, 6);
        manager.executeCommand(
                new EditCommand(editor, new Edit(5, null, deleted)));

        System.out.println(editor.getText()); // Hello

        manager.undo();
        System.out.println(editor.getText()); // Hello World

        manager.redo();
        System.out.println(editor.getText()); // Hello
    }
}