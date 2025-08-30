
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.zip.*;


public class HZipLite {
    // ---------- UI ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("HZipLite – single-file Huffman zipper");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(640, 260);
            f.setLocationRelativeTo(null);

            JButton compress = new JButton("Compress File/Folder…");
            JButton decompress = new JButton("Decompress .hzip…");
            JLabel status = new JLabel("Ready.");
            status.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            JProgressBar bar = new JProgressBar(); bar.setVisible(false);

            JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 16));
            center.add(compress); center.add(decompress);
            f.setLayout(new BorderLayout());
            f.add(bar, BorderLayout.NORTH);
            f.add(center, BorderLayout.CENTER);
            f.add(status, BorderLayout.SOUTH);

            compress.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                if (fc.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                    File in = fc.getSelectedFile();
                    File out = new File(in.getParentFile(), in.getName() + ".hzip");
                    runAsync(f, bar, status, "Compressing…", () -> {
                        long inSize = sizeOf(in);
                        HZipCore.compressSmart(in, out);
                        long outSize = out.length();
                        return "Compressed: " + human(inSize) + " → " + human(outSize)
                                + " (" + pct(outSize, inSize) + ") → " + out.getName();
                    });
                }
            });

            decompress.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                    File in = fc.getSelectedFile();
                    File out = new File(in.getParentFile(), strip(in.getName(), ".hzip") + "_unzipped");
                    runAsync(f, bar, status, "Decompressing…", () -> HZipCore.decompressSmart(in, out));
                }
            });

            f.setVisible(true);
        });
    }

    private static void runAsync(JFrame frame, JProgressBar bar, JLabel status, String msg, Task t) {
        setEnabledDeep(frame.getContentPane(), false);
        bar.setIndeterminate(true); bar.setVisible(true);
        status.setText(msg);
        new SwingWorker<>() {
            String res; Exception err;
            @Override protected Object doInBackground() { try { res = t.run(); } catch (Exception ex) { err = ex; } return null; }
            @Override protected void done() {
                bar.setIndeterminate(false); bar.setVisible(false);
                setEnabledDeep(frame.getContentPane(), true);
                status.setText(err != null ? "Error: " + err.getMessage() : res);
            }
        }.execute();
    }

    @FunctionalInterface interface Task { String run() throws Exception; }

    private static void setEnabledDeep(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof Container) for (Component ch : ((Container)c).getComponents()) setEnabledDeep(ch, enabled);
    }
    private static String human(long b) {
        String[] u = {"B","KB","MB","GB"}; double x=b; int i=0; while (x>=1024 && i<u.length-1){x/=1024;i++;}
        return new java.text.DecimalFormat("#.##").format(x) + " " + u[i];
    }
    private static String pct(long out, long in){ if(in<=0) return "100%"; return new java.text.DecimalFormat("#.##").format((out*100.0)/in) + "%"; }
    private static String strip(String s, String suf){ return s.endsWith(suf) ? s.substring(0,s.length()-suf.length()) : s; }
    private static long sizeOf(File f){
        if (!f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total=0; File[] kids=f.listFiles(); if (kids!=null) for(File k:kids) total+=sizeOf(k);
        return total;
    }

    // ---------- Core in nested static classes (single file) ----------
    static final class HZipCore {
        private static final int MAGIC = 0x485A4951; // "HZIQ" (v2 lite)
        private static final int TYPE_FILE = 0, TYPE_ARCHIVE_ZIP = 1;

        static String decompressSmart(File inHzip, File outTarget) throws IOException {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(inHzip)))) {
                int magic = dis.readInt(); if (magic != MAGIC) throw new IOException("Not an HZIP-Lite file");
                int type = dis.readInt();
                String originalName = readUTF8(dis);
                int version = dis.readUnsignedShort(); // reserved
                long originalLen = dis.readLong();
                long crc32 = dis.readInt() & 0xFFFFFFFFL;

                // read tree
                int treeLen = dis.readInt();
                byte[] treeBytes = dis.readNBytes(treeLen);
                Node root; try (DataInputStream td = new DataInputStream(new ByteArrayInputStream(treeBytes))) {
                    root = readTree(td);
                }

                // rest: bitstream + pad
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                int nx; while ((nx = dis.read()) != -1) payload.write(nx);
                byte[] all = payload.toByteArray();
                if (all.length == 0) throw new IOException("Corrupt payload");
                int pad = all[all.length-1] & 0xFF;
                byte[] bitstream = Arrays.copyOf(all, all.length-1);

                // decode
                ByteArrayOutputStream decoded = new ByteArrayOutputStream((int)Math.min(originalLen, Integer.MAX_VALUE));
                try (BitInputStream bis = new BitInputStream(new ByteArrayInputStream(bitstream))) {
                    for (long produced = 0; produced < originalLen; produced++) {
                        Node cur = root;
                        while (!cur.isLeaf()) {
                            int bit = bis.readBit();
                            if (bit == -1) throw new IOException("Unexpected EOF");
                            cur = (bit==0) ? cur.left : cur.right;
                        }
                        decoded.write(cur.value);
                    }
                }
                byte[] payloadDecoded = decoded.toByteArray();
                if (CRC32Util.crc(payloadDecoded) != crc32) throw new IOException("CRC mismatch (file corrupted)");

                if (type == TYPE_FILE) {
                    // Restore with original name
                    File outFile = new File(outTarget.getParentFile(), originalName);
                    try (OutputStream fout = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        fout.write(payloadDecoded);
                    }
                    return "Decompressed file → " + outFile.getAbsolutePath();
                } else if (type == TYPE_ARCHIVE_ZIP) {
                    if (!outTarget.exists()) outTarget.mkdirs();
                    File folder = new File(outTarget, originalName);
                    folder.mkdirs();
                    ZipUtil.unzipBytesToDirectory(payloadDecoded, folder);
                    return "Decompressed folder → " + folder.getAbsolutePath();
                } else throw new IOException("Unknown type");
            }
        }

        static void compressSmart(File input, File outHzip) throws IOException {
            boolean isDir = input.isDirectory();
            byte[] payload;
            int type;
            if (isDir) {
                type = TYPE_ARCHIVE_ZIP;
                ByteArrayOutputStream z = new ByteArrayOutputStream();
                ZipUtil.zipDirectory(input, z);
                payload = z.toByteArray();
            } else {
                type = TYPE_FILE;
                payload = readAllBytes(input);
            }

            int[] freq = new int[256];
            for (byte b: payload) freq[b & 0xFF]++;
            Node root = buildTree(freq);
            Map<Byte,String> codes = new HashMap<>();
            buildCodes(root, "", codes);

            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outHzip)))) {
                dos.writeInt(MAGIC);
                dos.writeInt(type);
                writeUTF8(dos, input.getName());
                dos.writeShort(1); // version
                dos.writeLong(payload.length);
                dos.writeInt((int) CRC32Util.crc(payload));

                // tree
                ByteArrayOutputStream treeBuf = new ByteArrayOutputStream();
                try (DataOutputStream td = new DataOutputStream(treeBuf)) { writeTree(root, td); }
                byte[] treeBytes = treeBuf.toByteArray();
                dos.writeInt(treeBytes.length);
                dos.write(treeBytes);

                // bits
                try (BitOutputStream bos = new BitOutputStream(dos)) {
                    for (byte b : payload) bos.writeBits(codes.get(b));
                    int pad = bos.padToByte();
                    dos.writeByte(pad);
                }
            }
        }

        // ---- Huffman internals ----
        static Node buildTree(int[] freq) {
            PriorityQueue<Node> pq = new PriorityQueue<>();
            for (int i = 0; i < 256; i++) if (freq[i] > 0) pq.add(new Node(freq[i], (byte)i, null, null));
            if (pq.isEmpty()) return new Node(1, (byte)0, null, null);
            if (pq.size() == 1) {
                Node only = pq.poll(); return new Node(only.freq, null, only, null);
            }
            while (pq.size() > 1) {
                Node a = pq.poll(), b = pq.poll();
                pq.add(new Node(a.freq + b.freq, null, a, b));
            }
            return pq.poll();
        }
        static void buildCodes(Node n, String path, Map<Byte,String> map) {
            if (n.isLeaf()) map.put(n.value, path.isEmpty() ? "0" : path);
            else { buildCodes(n.left, path+"0", map); buildCodes(n.right, path+"1", map); }
        }
        static void writeTree(Node n, DataOutputStream out) throws IOException {
            if (n.isLeaf()) { out.writeByte(1); out.writeByte(n.value); }
            else { out.writeByte(0); writeTree(n.left, out); writeTree(n.right, out); }
        }
        static Node readTree(DataInputStream in) throws IOException {
            int tag = in.read(); if (tag == -1) throw new EOFException("Corrupt tree");
            if (tag == 1) { int v = in.read(); if (v == -1) throw new EOFException("Corrupt tree"); return new Node(0,(byte)v,null,null); }
            else if (tag == 0) { Node L = readTree(in), R = readTree(in); return new Node(0,null,L,R); }
            else throw new IOException("Bad tree tag");
        }
        static byte[] readAllBytes(File f) throws IOException {
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) { return in.readAllBytes(); }
        }
        static void writeUTF8(DataOutputStream dos, String s) throws IOException {
            byte[] b = s.getBytes("UTF-8"); dos.writeShort(b.length); dos.write(b);
        }
        static String readUTF8(DataInputStream dis) throws IOException {
            int len = dis.readUnsignedShort(); byte[] b = dis.readNBytes(len); return new String(b, "UTF-8");
        }
    }

    // ---------- tiny helpers below ----------
    static final class Node implements Comparable<Node> {
        final int freq; final Byte value; final Node left, right;
        Node(int f, Byte v, Node l, Node r){freq=f; value=v; left=l; right=r;}
        boolean isLeaf(){ return value != null; }
        public int compareTo(Node o){ return Integer.compare(this.freq, o.freq); }
    }
    static final class BitOutputStream implements AutoCloseable {
        private final OutputStream out; private int cur=0, n=0;
        BitOutputStream(OutputStream o){out=o;}
        void writeBit(int bit) throws IOException { cur = (cur<<1)| (bit&1); n++; if(n==8) flushByte(); }
        void writeBits(String s) throws IOException { for(int i=0;i<s.length();i++) writeBit(s.charAt(i)=='1'?1:0); }
        int padToByte() throws IOException { int pad = (n==0)?0:(8-n); for(int i=0;i<pad;i++) writeBit(0); return pad; }
        private void flushByte() throws IOException { out.write(cur & 0xFF); cur=0; n=0; }
        public void close() throws IOException { if (n>0){cur <<= (8-n); flushByte();} out.flush(); }
    }
    static final class BitInputStream implements AutoCloseable {
        private final InputStream in; private int cur=-1, n=0;
        BitInputStream(InputStream i){in=i;}
        int readBit() throws IOException { if(n==0){cur=in.read(); if(cur==-1) return -1; n=8;} n--; return (cur>>>n)&1; }
        public void close() throws IOException { in.close(); }
    }
    static final class ZipUtil {
        static void zipDirectory(File folder, OutputStream out) throws IOException {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out))) {
                String base = folder.getAbsolutePath();
                add(zos, folder, base.length()+1);
            }
        }
        static void add(ZipOutputStream zos, File f, int base) throws IOException {
            if (f.isDirectory()) {
                File[] kids = f.listFiles(); if (kids==null) return;
                for (File k : kids) add(zos, k, base);
            } else {
                String path = f.getAbsolutePath().substring(base).replace(File.separatorChar, '/');
                zos.putNextEntry(new ZipEntry(path));
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) { in.transferTo(zos); }
                zos.closeEntry();
            }
        }
        static void unzipBytesToDirectory(byte[] zip, File target) throws IOException {
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry e; while ((e = zis.getNextEntry()) != null) {
                    File out = new File(target, e.getName());
                    if (e.isDirectory()) out.mkdirs();
                    else { out.getParentFile().mkdirs(); try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out))) { zis.transferTo(o); } }
                    zis.closeEntry();
                }
            }
        }
    }
    static final class CRC32Util {
        static long crc(byte[] data) throws IOException {
            java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(data); return c.getValue();
        }
    }
}
