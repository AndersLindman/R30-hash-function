package r30;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Class for the R30 hash function. The R30 hash function is based on the Rule
 * 30 cellular automaton and produces 256-bit digests (hash values).
 * 
 * Copyright and license free.
 * 
 * @author Anders Lindman
 * 
 */
public class R30 {

	private final static int MAX_HASH_BYTES = 32;
	private final static int BLOCK_SIZE_BYTES = MAX_HASH_BYTES;

	/**
	 * Calculates the hash value for the specified input stream.
	 * @param is the message input stream
	 * @return 256-bit digest as bytes
	 * @throws IOException if failed to read input stream
	 */
	public final byte[] digest(InputStream is) throws IOException {
		byte[] digest = null;
		byte[] block = new byte[BLOCK_SIZE_BYTES];
		int bytesRead = 0;
		long totalBytesRead = 0;
		while (bytesRead != -1) {
			bytesRead = 0;
			int blockBytesRead = 0;
			while (blockBytesRead < BLOCK_SIZE_BYTES && bytesRead != -1) {
				bytesRead = is.read(block, blockBytesRead, 
						BLOCK_SIZE_BYTES - blockBytesRead);
				if (bytesRead > 0) {
					blockBytesRead += bytesRead;
				}
			}
			totalBytesRead += blockBytesRead;
			if (blockBytesRead < BLOCK_SIZE_BYTES) {
				for (int i = blockBytesRead; i < BLOCK_SIZE_BYTES; i++) {
					block[i] = 0;
				}
				if (BLOCK_SIZE_BYTES - blockBytesRead >= 8) {
					for (int i = 1, j = 0; i < 9; i++, j += 8) {
						block[BLOCK_SIZE_BYTES - i] =
								(byte) (totalBytesRead >>> j);
					}
				} else {
					bytesRead = 0;
				}
			}
			if (digest != null) {
				for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
					block[i] ^= digest[i];
				}
			} else {
				digest = new byte[BLOCK_SIZE_BYTES];
			}
			byte[] nextDigest = digestBlock(block);
			for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
				digest[i] ^= nextDigest[i];
			}
		}
		return digest;
	}
	
	private final byte[] digestBlock(byte[] block) {
		int maxKeyBytes = BLOCK_SIZE_BYTES + 1;
		int maxKeyBits = maxKeyBytes * 8;
		byte[] hash = new byte[MAX_HASH_BYTES];
		byte[] key = new byte[maxKeyBytes];
		for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
			key[i] = block[i];
		}
		key[BLOCK_SIZE_BYTES] = 0x01;
		int maxHashBits = MAX_HASH_BYTES * 8;
		int skipRows = maxKeyBits * 7;
		int maxCells = 2;
		maxCells += maxKeyBits;
		maxCells += skipRows;
		maxCells += maxHashBits * 2;
		int maxLongs = (maxCells + 63) >>> 6;
		maxCells = maxLongs << 6;
		int cellsMid = maxCells / 2;		
		long[] cells = new long[maxLongs];
		int keyStart = (maxCells - maxKeyBits) / 2;
		for (int i = 0; i < key.length; i++) {
			int keyChar = key[i];
			int bitPos = 0x80;
			for (int j = 0; j < 8; j++) {
				long b = (keyChar & bitPos) >>> (7 - j);
				int bitIndex = keyStart + i * 8 + j;
				cells[bitIndex >>> 6] |= b << (63 - (bitIndex % 64));
				bitPos >>>= 1;
			}
		}
		int bitCount = 0;
		int mid = 0;
		int longMid = maxLongs / 2;
		int longMidShift = longMid * 2 == maxLongs ? 63 : 31;
		int maxRow = skipRows + maxHashBits * 2;
		for (int row = 0; row < maxRow; row++) {
			int doubleRow = row * 2;
			int calcWidth = doubleRow;
			if (calcWidth > maxRow - 2) {
				calcWidth = maxRow - ((doubleRow) % maxRow) + 2;
			} else {
				calcWidth += maxKeyBits;
			}
			int halfWidth = calcWidth / 2 + 2;
			int start = (cellsMid - halfWidth) >>> 6;
			int end = (cellsMid + halfWidth + 63) >>> 6;
			mid = (int) ((cells[longMid] >>> longMidShift) & 0x01);
			long carryLeft = 0L;
			for (int i = start; i < end; i++) {
				long l = cells[i];
				long carryRight = i < maxLongs - 1 ?
						cells[i + 1] >>> 63 : 0;
				long cellRight = (l << 1) | carryRight;
				long cellLeft = (l >>> 1) | carryLeft;
				carryLeft = l << 63;
				cells[i] = cellLeft ^ (l | cellRight);
			}
			if (row < skipRows) {
				continue;
			}
			if (row % 2 == 1) {
				if (mid == 1) {
					int bufPos = bitCount >>> 3;
					hash[bufPos] |= 1 << (7 - (bitCount % 8));
				}
				bitCount++;
			}
		}
		return hash;
	}

	/**
	 * Calculates the hash value for the specified message bytes.
	 * @param message the message bytes
	 * @return 256-bit digest as bytes
	 */
	public final byte[] digest(byte[] message) {
		byte[] hash = null;
		try {
			hash = digest(new ByteArrayInputStream(message));
		} catch (IOException e) {}
		return hash;
	}

	/**
	 * Calculates the hash value for the specified message 
	 * and character set.
	 * @param message the message string
	 * @param charset the character set to be used to encode the string 
	 * @return 256-bit digest as bytes
	 */
	public final byte[] digest(String message, Charset charset) {
		return digest(message.getBytes(charset));
	}

	/**
	 * Calculates the hash value for the specified message.
	 * @param message the message string
	 * @return 256-bit digest as a hex string
	 */
	public final String digest(String message) {
		byte[] hash = digest(message.getBytes());
		return getHexString(hash);
	}

	/**
	 * Converts the specified hash to a hex string.
	 * @param digest the hash value bytes
	 * @return hex string of the hash value
	 */
	public String getHexString(byte[] digest) {
		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		String message = "hello, world";
		R30 r30 = new R30();
		String hash = r30.digest(message);
		System.out.println("The R30 hash value for \"" + message + "\" is: " + hash);
	}
}