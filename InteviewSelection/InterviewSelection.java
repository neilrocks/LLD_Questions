import java.util.Scanner;

abstract class InterviewHandler{
    protected InterviewHandler nextHandler;
    public InterviewHandler setNextHandler(InterviewHandler handler) {
        this.nextHandler = handler;
        return handler;
    }
    protected abstract void hire(String candidateName);
}
class TechnicalInterviewHandler extends InterviewHandler{

    @Override
    protected void hire(String candidateName) {
        System.out.println("Enter Tech Interview Score for " + candidateName);
        int score = new Scanner(System.in).nextInt();
        if(score>=70) {
            System.out.println(candidateName + " passed the Technical Interview.");
            if(nextHandler != null) {
                nextHandler.hire(candidateName);
            }
        } else {
            System.out.println(candidateName + " failed the Technical Interview.");
        }
    }
}
class BarRaiserInterviewHandler extends InterviewHandler{

    @Override
    protected void hire(String candidateName) {
        System.out.println("Enter Bar Raiser Interview Score for " + candidateName);
        int score = new Scanner(System.in).nextInt();
        if(score>=75) {
            System.out.println(candidateName + " passed the Bar Raiser Interview.");
            if(nextHandler != null) {
                nextHandler.hire(candidateName);
            }
        } else {
            System.out.println(candidateName + " failed the Bar Raiser Interview.");
        }
    }
}
class HRInterviewHandler extends InterviewHandler{

    @Override
    protected void hire(String candidateName) {
        System.out.println("Enter HR Interview Score for " + candidateName);
        int score = new Scanner(System.in).nextInt();
        if(score>=80) {
            System.out.println(candidateName + " passed the HR Interview.");
            System.out.println(candidateName + " has been hired!");
        } else {
            System.out.println(candidateName + " failed the HR Interview.");
        }
    }
}
class InterviewProcess{
    protected InterviewHandler handler;
    public InterviewProcess() {
        handler=new TechnicalInterviewHandler();
        handler.setNextHandler(new BarRaiserInterviewHandler()).setNextHandler(new HRInterviewHandler());
    }
    public void startProcess(String candidateName) {
        handler.hire(candidateName);
    }
}
class InterviewSelection {
    public static void main(String[] args) {
        System.out.println("Welcome to the Interview Selection Process!");
        InterviewProcess interviewProcess = new InterviewProcess();
        interviewProcess.startProcess("Alice");
    }
}
