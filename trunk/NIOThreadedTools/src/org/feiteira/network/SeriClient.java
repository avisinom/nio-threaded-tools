package org.feiteira.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

public class SeriClient {
	private static Logger log = Logger.getLogger(SeriClient.class);

	public static final int DEFAULT_SLEEP_TIME = 10;
	public static final int DEFAULT_TIMEOUT = 10000;

	private String host;
	private int port;
	private SocketChannel socket;

	private int sleep_time;
	private int timeout;

	private String tag = "";

	public SeriClient(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
		this.sleep_time = DEFAULT_SLEEP_TIME;
		this.timeout = DEFAULT_TIMEOUT;

		try {
			reconnect();
		} catch (IOException ioe) {
			log.warn("Could not connect to server, retrying once.", ioe);
			reconnect();
			log.warn("Reconnect successful");
		}
	}

	public void reconnect() throws IOException {
		socket = SocketChannel.open();
		socket.configureBlocking(false);
		InetSocketAddress ineta = new InetSocketAddress(host, port);
		socket.connect(ineta);

		long startTime = System.currentTimeMillis();
		while (!socket.finishConnect()) {
			try {
				Thread.sleep(this.sleep_time);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (System.currentTimeMillis() - startTime > this.timeout)
				throw new IOException(
						"Could not connect to server, timeout after "
								+ this.timeout + " mils");
		}
	}

	public SeriDataPackage read() throws IOException {
		ByteBuffer lengthByteBuffer = ByteBuffer.wrap(new byte[4]);
		long startTime = System.currentTimeMillis();

		do {
			// read from socket, should return the data size
			int err = socket.read(lengthByteBuffer);
			if (err == -1) {
				socket.close();
				return null;
			}

			// Socket times out
			if (System.currentTimeMillis() - startTime > this.timeout) {
				socket.close();
				return null;
			}

			try {
				Thread.sleep(this.sleep_time);
			} catch (InterruptedException e) {
			}

		} while (lengthByteBuffer.remaining() != 0);

		ByteBuffer dataByteBuffer = ByteBuffer.allocate(lengthByteBuffer
				.getInt(0));

		// int size = lengthByteBuffer.getInt(0);
		while (true) {
			int err = socket.read(dataByteBuffer);
			if (err == -1) {
				socket.close();
				return null;
			}

			if (dataByteBuffer.remaining() == 0) {
				ObjectInputStream ois = new ContextualObjectInputStream(
						new ByteArrayInputStream(dataByteBuffer.array()));
				Serializable retObj;
				try {
					retObj = (Serializable) ois.readObject();
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(
							"Serializable not found? Really weird!", e);
				} finally {
					ois.close();
				}
				// clean up
				dataByteBuffer = null;

				SeriDataPackage ret = new SeriDataPackage();
				ret.setObject(retObj);
				ret.setSocket(socket);
				return ret;
			}

			// Socket times out
			if (System.currentTimeMillis() - startTime > this.timeout) {
				socket.close();
				return null;
			}

			try {
				Thread.sleep(this.sleep_time);
			} catch (InterruptedException e) {
			}
		}
	}

	public synchronized void send(Serializable objectToSend) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (int i = 0; i < 4; i++)
			baos.write(0);
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(objectToSend);
		oos.close();
		final ByteBuffer wrap = ByteBuffer.wrap(baos.toByteArray());
		wrap.putInt(0, baos.size() - 4);
		socket.write(wrap);
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getSleep_time() {
		return sleep_time;
	}

	public void setSleep_time(int sleep_time) {
		this.sleep_time = sleep_time;
	}

	public void close() {
		try {
			this.socket.close();
		} catch (IOException e) {
			// I really don't care..
		}
	}

	public String getTag() {
		return tag;
	}

	public void seTag(String logTag) {
		this.tag = logTag;
	}
}
