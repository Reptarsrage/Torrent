JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	Utility.java \
	ServerProcessConnection.java \
	ServerListener.java \
	Client.java \
	ClientConnection.java \
	ClientObj.java \
	ClientReceiver.java \
	ClientSender.java \
	FileWriter.java \
	Constants.java \
	Server.java \
	ServerFile.java \
	ServerFwding.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
