import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * 
 * @author Mikael GUILLEMOT
 * 
 */
public class Usrv {

	private static final int MaxConnectionPerIP = 8;

	private static int PORT = 8080;
	private static String message = "hello";
	private ServerSocket listener = null;
	private DateFormat timeStamp = null;
	private boolean run = true;

	private Hashtable<String, Integer> addressfilter;

	private Usrv() {
		timeStamp = new SimpleDateFormat("[dd/MM,hh:mm:ss] ");
		addressfilter = new Hashtable<>();
	}

	// pour l'arret du serveur en cas de fermeture (ie Alt+F4)
	private class Sh extends Thread {
		public void run() {
			System.out.print("shuting down server...");
			try {
				listener.close();
			} catch (IOException e) {
				System.exit(-1);
			}
			System.out.println("done");
		}
	}

	// gestion d'un client (thread)
	private class Session extends Thread {
		private Socket clientSocket;
		private BufferedReader in;
		private DataOutputStream out;
		private byte[] adr;
		private String adresse;

		public Session(Socket client) {
			clientSocket = client;
			boolean ReadyToStart = false;
			try {
				adr = clientSocket.getInetAddress().getAddress();
				adresse = bv(adr[0]) + "." + bv(adr[1]) + "." + bv(adr[2]) + "." + bv(adr[3]);

				int cnxPerIp = 0;
				if (addressfilter.containsKey(adresse))
					cnxPerIp = addressfilter.get(adresse);
				cnxPerIp++;
				if (cnxPerIp < MaxConnectionPerIP) {
					in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					out = new DataOutputStream(clientSocket.getOutputStream());
					ReadyToStart = true;
					addressfilter.put(adresse, cnxPerIp);
				} else
					clientSocket.close();
			} catch (IOException e) {
				log("erreur <" + adresse + "> : " + e.getMessage());
			}
			if (ReadyToStart)
				this.start();
		}

		public void run() {
			try {
				String request = in.readLine();
				StringTokenizer st = new StringTokenizer(request, " ");
				String cmd_get = st.nextToken();
				String cmd_page = st.nextToken();
				if (cmd_get.equals("GET")) {
					if (!cmd_page.equals("/favicon.ico")) {
						log("<" + adresse + ">Reception requette : " + request);
						String e404 = "<html><head></head>" + "<body><p align=\"center\"><br/>" + message
								+ "<br/><br/><b>[" + adresse + "]</b> <p/></body></html>";
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Content-Length: " + e404.length() + "\r\n");
						out.writeBytes("Content-Type: text/html\r\n\r\n");
						out.writeBytes(e404);
						out.flush();
					}
				} else {
					out.writeBytes("400 Bad Request");
					out.flush();
				}
				clientSocket.close();
			} catch (Exception e) {
				log("error <" + adresse + "> : " + e.getMessage());
			} finally {
				addressfilter.put(adresse, addressfilter.get(adresse) - 1);
			}
		}
	}

	private void run() {
		try {
			listener = new ServerSocket(PORT);

		} catch (IOException e) {
			System.out.println("Server cannot start : " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		// si on peu ouvrir une connexion (creation ServerSocket) on doit la
		// ferner a l'arret d'ou la procedure d'extinction Sh
		Runtime.getRuntime().addShutdownHook(new Sh());

		System.out.println("waiting for client...");
		int clean = 0;
		try {
			while (run) {
				// on previen le gc qu'il y a du menage a faire
				Socket client;
				client = listener.accept();
				// la session est threadé et autonome
				new Session(client);
				clean++;
				if (clean > 10000) {
					System.gc();
					clean = 0;
				}
			}
		} catch (IOException e) {
			log("Server stop(" + e.getMessage() + ")");
		}
	}

	// renvoi la valeur (integer) d'un byte, non signe
	int bv(byte b) {
		int out = b;
		if (b < 0)
			out = out + 256;
		return out;
	}

	private synchronized void log(String msg) {
		System.out.println(timeStamp.format(new Date())+msg);
	}

	public static void main(String[] args) {

		if (args.length > 2 ||(args.length == 1 && args[0].contains("h"))) {
			System.out.println("args : [port] [\"html message\"], to use last args, other must be defined.");
			System.exit(0);
		}
		if (args.length > 0)
			PORT = Integer.parseInt(args[0]);
		if (args.length > 1)
			message = args[1];

		System.out.println("server starting on port " + PORT + ", message is: " + message);
		Usrv serveur = new Usrv();
		serveur.run();
	}
}
