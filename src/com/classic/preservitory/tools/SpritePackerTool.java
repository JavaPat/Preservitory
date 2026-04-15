package com.classic.preservitory.tools;

import com.classic.preservitory.cache.CacheConfig;
import com.classic.preservitory.cache.SpriteCache;
import com.classic.preservitory.cache.SpriteEntry;
import com.classic.preservitory.cache.SpriteIndex;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone sprite packing tool.
 *
 * Run this tool independently — it must NOT be launched by the game at runtime.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Import File</b>   — add one or more individual PNG files.</li>
 *   <li><b>Import Folder</b> — recursively scans the entire directory tree and imports
 *       all {@code .png} files.  Sprite IDs are derived from the path relative to the
 *       chosen root (e.g. {@code enemy/goblin/walk/north/frame_0}).</li>
 *   <li><b>Export Selected / All</b> — write sprites back to PNG files.</li>
 *   <li><b>Pack</b>   — write {@code sprites.dat} + {@code sprites.idx}.</li>
 *   <li><b>Reload</b> — re-read the existing packed cache from disk.</li>
 *   <li><b>Select All / Clear</b> — convenience selection controls.</li>
 * </ul>
 *
 * <h3>Animation detection</h3>
 * After every import the tool automatically analyses each sprite's ID path to detect
 * entity name, animation type, direction, and frame number — no {@code metadata.json}
 * needed.  Detected animations appear in the lower "Animations" panel; clicking a row
 * there selects the corresponding sprites in the upper table.
 *
 * <p>Expected path convention:
 * <pre>  entity/type/animationType/direction/frame_N.png</pre>
 * Example:
 * <pre>  enemy/goblin/walk/north/frame_0.png</pre>
 *
 * <h3>Output location</h3>
 * <ol>
 *   <li>User's local cache ({@code ~/.preservitory/cache/})</li>
 *   <li>Project-relative {@code cache/} directory</li>
 * </ol>
 */
public final class SpritePackerTool extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(SpritePackerTool.class.getName());

    // =========================================================================
    //  Inner types — data model
    // =========================================================================

    /** Mutable sprite record shown in the flat sprite table. */
    private static final class PackSprite {
        /** Sprite lookup key — editable in the table before packing. */
        String        id;
        /**
         * Full relative path from the import root (e.g. {@code walk/north/frame_0.png}).
         * For single-file imports this is just the filename.
         */
        String        displayPath;
        /** Absolute path to the source PNG, or {@code null} if loaded from an existing pack. */
        Path          sourcePath;
        /** Decoded image; may be {@code null} until {@link #ensureImageLoaded} is called. */
        BufferedImage image;

        PackSprite(String id, String displayPath, Path sourcePath, BufferedImage image) {
            this.id          = id;
            this.displayPath = displayPath;
            this.sourcePath  = sourcePath;
            this.image       = image;
        }

        int width()  { return image != null ? image.getWidth()  : 0; }
        int height() { return image != null ? image.getHeight() : 0; }
    }

    // -------------------------------------------------------------------------

    /** Automatically detected result of parsing a sprite ID path. */
    private record DetectedPath(
            /** Human-readable entity identifier, e.g. {@code "enemy/goblin"}. */
            String entity,
            /** Animation name, e.g. {@code "walk"}, or {@code null} if not detected. */
            String animType,
            /** Direction name, e.g. {@code "north"}, or {@code null} if not detected. */
            String direction,
            /** Zero-based frame index extracted from the filename. */
            int frameNum
    ) {}

    // -------------------------------------------------------------------------

    /**
     * Stateless utility that parses a sprite ID path to detect its animation structure.
     *
     * <p>No metadata files are required.  Detection is based on well-known keyword
     * components in the path:
     * <ul>
     *   <li>Animation keywords: {@code walk, run, attack, idle, death, hurt, cast, punch, block, rotations}</li>
     *   <li>Direction keywords: {@code north, south, east, west}</li>
     * </ul>
     */
    private static final class PathParser {

        private static final Set<String> ANIM_KEYWORDS = Set.of(
                "walk", "run", "attack", "idle", "death", "dead",
                "hurt", "cast", "punch", "block", "rotations", "anim"
        );

        private static final Set<String> DIR_KEYWORDS = Set.of(
                "north", "south", "east", "west"
        );

        private PathParser() {}

        /**
         * Parses {@code id} (a forward-slash-separated path without extension)
         * and returns a {@link DetectedPath} describing the animation structure found.
         */
        static DetectedPath parse(String id) {
            if (id == null || id.isBlank()) return new DetectedPath("unknown", null, null, 0);

            String[] parts = id.split("/");
            int animIdx = -1;
            int dirIdx  = -1;

            for (int i = 0; i < parts.length; i++) {
                String p = parts[i].toLowerCase();
                if (animIdx < 0 && ANIM_KEYWORDS.contains(p)) animIdx = i;
                if (dirIdx  < 0 && DIR_KEYWORDS .contains(p)) dirIdx  = i;
            }

            // Entity = everything before the animation keyword
            String entity;
            if (animIdx > 0) {
                entity = String.join("/", Arrays.copyOf(parts, animIdx));
            } else if (animIdx == 0) {
                entity = "unknown";
            } else {
                // No recognised animation keyword — use parent path segments (all but filename)
                entity = parts.length > 1
                        ? String.join("/", Arrays.copyOf(parts, parts.length - 1))
                        : "unknown";
            }

            String animType  = animIdx >= 0 ? parts[animIdx].toLowerCase() : null;
            String direction = dirIdx  >= 0 ? parts[dirIdx].toLowerCase()  : null;
            int    frameNum  = extractFrameNum(parts[parts.length - 1]);

            return new DetectedPath(entity, animType, direction, frameNum);
        }

        /**
         * Extracts a frame number from a filename stem.
         * Tries {@code frame_N} first, then falls back to the first digit sequence found.
         * Returns 0 if no number is found.
         */
        private static int extractFrameNum(String name) {
            Matcher m = Pattern.compile("frame_(\\d+)").matcher(name);
            if (m.find()) return Integer.parseInt(m.group(1));
            m = Pattern.compile("(\\d+)").matcher(name);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * An automatically-detected animation group: one entity × one animation type
     * × one direction, holding its frames in frame-number order.
     */
    private static final class AnimationGroup {
        final String entity;
        final String animType;
        final String direction;

        /** (frameNumber, sprite) pairs, sorted after {@link #sortFrames()}. */
        private final List<Map.Entry<Integer, PackSprite>> frames = new ArrayList<>();

        AnimationGroup(String entity, String animType, String direction) {
            this.entity    = entity;
            this.animType  = animType != null  ? animType  : "—";
            this.direction = direction != null ? direction : "—";
        }

        void addFrame(PackSprite sprite, int frameNum) {
            frames.add(Map.entry(frameNum, sprite));
        }

        void sortFrames() {
            frames.sort(Map.Entry.comparingByKey());
        }

        int frameCount() { return frames.size(); }

        /** Returns sprites in frame-number order. */
        List<PackSprite> sortedSprites() {
            return frames.stream().map(Map.Entry::getValue).toList();
        }
    }

    // =========================================================================
    //  Inner types — table models
    // =========================================================================

    private static final class SpriteTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"ID", "Width", "Height", "Source"};
        private final List<PackSprite> sprites;

        SpriteTableModel(List<PackSprite> sprites) { this.sprites = sprites; }

        @Override public int    getRowCount()    { return sprites.size(); }
        @Override public int    getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            PackSprite s = sprites.get(row);
            return switch (col) {
                case 0  -> s.id;
                case 1  -> s.width();
                case 2  -> s.height();
                case 3  -> s.displayPath != null ? s.displayPath : "<packed>";
                default -> null;
            };
        }

        @Override public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 && value instanceof String s) {
                sprites.get(row).id = s.strip();
                fireTableCellUpdated(row, col);
            }
        }

        PackSprite get(int row) { return sprites.get(row); }
        int        size()       { return sprites.size(); }

        void add(PackSprite s) {
            int idx = sprites.size();
            sprites.add(s);
            fireTableRowsInserted(idx, idx);
        }

        void replace(int row, PackSprite s) {
            sprites.set(row, s);
            fireTableRowsUpdated(row, row);
        }

        /** Removes the rows at the given (unsorted) indices. */
        void removeRows(int[] rows) {
            int[] sorted = rows.clone();
            Arrays.sort(sorted);
            for (int i = sorted.length - 1; i >= 0; i--) sprites.remove(sorted[i]);
            fireTableDataChanged();
        }

        void clear() {
            int n = sprites.size();
            sprites.clear();
            if (n > 0) fireTableRowsDeleted(0, n - 1);
        }
    }

    // -------------------------------------------------------------------------

    private static final class AnimationTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Entity", "Animation", "Direction", "Frames"};
        private final List<AnimationGroup> groups;

        AnimationTableModel(List<AnimationGroup> groups) { this.groups = groups; }

        @Override public int    getRowCount()    { return groups.size(); }
        @Override public int    getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            AnimationGroup g = groups.get(row);
            return switch (col) {
                case 0  -> g.entity;
                case 1  -> g.animType;
                case 2  -> g.direction;
                case 3  -> g.frameCount();
                default -> null;
            };
        }

        AnimationGroup get(int row) { return groups.get(row); }

        void setGroups(List<AnimationGroup> newGroups) {
            groups.clear();
            groups.addAll(newGroups);
            fireTableDataChanged();
        }
    }

    // =========================================================================
    //  Fields
    // =========================================================================

    private final List<PackSprite>     spriteList      = new ArrayList<>();
    private final SpriteTableModel     tableModel      = new SpriteTableModel(spriteList);
    private final JTable               table           = new JTable(tableModel);

    private final List<AnimationGroup> animGroups      = new ArrayList<>();
    private final AnimationTableModel  animTableModel  = new AnimationTableModel(animGroups);
    private final JTable               animTable       = new JTable(animTableModel);

    private final JLabel               statusLabel     = new JLabel(" Ready.");
    private final JLabel               previewLabel    = new JLabel();

    // =========================================================================
    //  Entry point
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new SpritePackerTool().setVisible(true);
        });
    }

    // =========================================================================
    //  Construction
    // =========================================================================

    public SpritePackerTool() {
        super("Preservitory — Sprite Packer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1020, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(4, 4));

        add(buildToolbar(),   BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // Sprite table selection → update preview
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview();
        });

        // Animation table selection → cross-select sprites in the sprite table
        animTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) syncSpriteSelectionFromAnim();
        });

        // Double-click sprite → full-size preview dialog
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showPreviewDialog();
            }
        });
    }

    // =========================================================================
    //  UI construction
    // =========================================================================

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton importFile   = new JButton("Import File");
        JButton importFolder = new JButton("Import Folder");
        JButton exportSel    = new JButton("Export Selected");
        JButton exportAll    = new JButton("Export All");
        JButton remove       = new JButton("Remove Selected");
        JButton selectAll    = new JButton("Select All");
        JButton clearSel     = new JButton("Clear Selection");
        JButton pack         = new JButton("Pack");
        JButton reload       = new JButton("Reload Pack");

        importFile  .addActionListener(e -> onImportFile());
        importFolder.addActionListener(e -> onImportFolder());
        exportSel   .addActionListener(e -> onExportSelected());
        exportAll   .addActionListener(e -> onExportAll());
        remove      .addActionListener(e -> onRemoveSelected());
        selectAll   .addActionListener(e -> table.selectAll());
        clearSel    .addActionListener(e -> table.clearSelection());
        pack        .addActionListener(e -> onPack());
        reload      .addActionListener(e -> onReload());

        pack  .setFont(pack  .getFont().deriveFont(Font.BOLD));
        reload.setFont(reload.getFont().deriveFont(Font.ITALIC));

        bar.add(importFile);
        bar.add(importFolder);
        bar.addSeparator();
        bar.add(exportSel);
        bar.add(exportAll);
        bar.addSeparator();
        bar.add(remove);
        bar.add(selectAll);
        bar.add(clearSel);
        bar.addSeparator();
        bar.add(pack);
        bar.add(reload);

        return bar;
    }

    private JSplitPane buildCenter() {
        // ---- Sprite table (top-left) ----
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(1).setPreferredWidth(55);
        table.getColumnModel().getColumn(2).setPreferredWidth(55);
        table.getColumnModel().getColumn(3).setPreferredWidth(300);

        // ---- Animation table (bottom-left) ----
        animTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        animTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        animTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        animTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        animTable.getColumnModel().getColumn(3).setPreferredWidth(60);

        JPanel animPanel = new JPanel(new BorderLayout(2, 2));
        animPanel.add(new JLabel("  Detected Animations  (click row to select sprites)",
                                 SwingConstants.LEFT), BorderLayout.NORTH);
        animPanel.add(new JScrollPane(animTable), BorderLayout.CENTER);

        // ---- Left: sprite table on top, animations below ----
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), animPanel);
        leftSplit.setResizeWeight(0.60);

        // ---- Right: preview ----
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewLabel.setText("(select a sprite)");
        previewLabel.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setPreferredSize(new Dimension(260, 260));
        previewPanel.setMinimumSize(new Dimension(200, 200));
        previewPanel.add(new JLabel("Preview", SwingConstants.CENTER), BorderLayout.NORTH);
        previewPanel.add(previewLabel, BorderLayout.CENTER);

        JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, previewPanel);
        outer.setResizeWeight(0.82);
        return outer;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel countLabel = new JLabel();
        tableModel    .addTableModelListener(e -> countLabel.setText(
                tableModel.getRowCount() + " sprites, " +
                animTableModel.getRowCount() + " anim groups  "));
        animTableModel.addTableModelListener(e -> countLabel.setText(
                tableModel.getRowCount() + " sprites, " +
                animTableModel.getRowCount() + " anim groups  "));
        bar.add(countLabel, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    //  Actions
    // =========================================================================

    private void onImportFile() {
        JFileChooser chooser = newPngChooser("Import Sprite");
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        int firstNew = spriteList.size();
        int added = 0;
        for (java.io.File f : chooser.getSelectedFiles()) {
            Path p = f.toPath();
            if (addOrReplace(stripExtension(p.getFileName().toString()),
                             p.getFileName().toString(), p)) added++;
        }
        refreshAnimations();
        setStatus("Imported " + added + " sprite(s).");
        selectNewRows(firstNew);
    }

    private void onImportFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Folder — all PNGs inside will be imported recursively");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);  // hide "All files" — only folders are valid here
        // Default to the cache sprites directory if it exists
        java.io.File spritesDir = Paths.get(CacheConfig.CACHE_DIR, "sprites").toFile();
        if (spritesDir.isDirectory()) chooser.setCurrentDirectory(spritesDir);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path root     = chooser.getSelectedFile().toPath();
        int  firstNew = spriteList.size();
        int  added    = importFolder(root);
        refreshAnimations();
        setStatus("Imported " + added + " sprite(s) from " + root.getFileName() + ".");
        selectNewRows(firstNew);
    }

    private void onExportSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) { setStatus("No sprites selected."); return; }

        if (rows.length == 1) {
            PackSprite s = tableModel.get(rows[0]);
            if (!ensureImageLoaded(s)) { setStatus("Could not load image for '" + s.id + "'."); return; }
            JFileChooser chooser = newPngChooser("Export Sprite As");
            chooser.setSelectedFile(new java.io.File(s.id.replace('/', '_') + ".png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            exportSprite(s, chooser.getSelectedFile().toPath());
            setStatus("Exported '" + s.id + "'.");
        } else {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export " + rows.length + " Sprites — Select Output Folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path outDir   = chooser.getSelectedFile().toPath();
            int  exported = 0;
            for (int row : rows) {
                PackSprite s = tableModel.get(row);
                if (ensureImageLoaded(s)) {
                    exportSprite(s, outDir.resolve(s.id.replace('/', '_') + ".png"));
                    exported++;
                }
            }
            setStatus("Exported " + exported + " sprite(s) to " + outDir + ".");
        }
    }

    private void onExportAll() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export All — Select Output Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path outDir   = chooser.getSelectedFile().toPath();
        int  exported = 0;
        for (PackSprite s : spriteList) {
            if (ensureImageLoaded(s)) {
                // Preserve sub-folder structure
                exportSprite(s, outDir.resolve(s.id.replace('\\', '/') + ".png"));
                exported++;
            }
        }
        setStatus("Exported " + exported + " sprite(s) to " + outDir + ".");
    }

    private void onRemoveSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) return;
        tableModel.removeRows(rows);
        if (!spriteList.isEmpty()) {
            int next = Math.min(rows[0], spriteList.size() - 1);
            table.setRowSelectionInterval(next, next);
        }
        refreshAnimations();
        setStatus("Removed " + rows.length + " sprite(s).");
    }

    private void onPack() {
        if (spriteList.isEmpty()) { setStatus("Nothing to pack."); return; }

        Path outDir = resolveOutputDir();
        if (outDir == null) { setStatus("ERROR: Could not determine output directory."); return; }

        Path datPath = outDir.resolve("sprites.dat");
        Path idxPath = outDir.resolve("sprites.idx");
        List<SpriteEntry> entries = new ArrayList<>(spriteList.size());
        long offset = 0;

        try (OutputStream datOut = new BufferedOutputStream(Files.newOutputStream(datPath))) {
            for (PackSprite s : spriteList) {
                if (!ensureImageLoaded(s)) {
                    LOGGER.warning("[Packer] Skipping '" + s.id + "' — could not load image.");
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(s.image, "PNG", bos);
                byte[] bytes = bos.toByteArray();
                datOut.write(bytes);
                entries.add(new SpriteEntry(s.id, offset, bytes.length,
                                            s.image.getWidth(), s.image.getHeight()));
                offset += bytes.length;
            }
        } catch (IOException ex) {
            setStatus("ERROR writing sprites.dat: " + ex.getMessage());
            return;
        }

        try {
            SpriteIndex.write(idxPath, entries);
        } catch (IOException ex) {
            setStatus("ERROR writing sprites.idx: " + ex.getMessage());
            return;
        }

        SpriteCache.reload();
        setStatus("Packed " + entries.size() + " sprite(s) → " + outDir + ".");
    }

    private void onReload() {
        SpriteCache.reload();
        spriteList.clear();
        tableModel.fireTableDataChanged();

        if (!SpriteCache.isAvailable()) {
            animTableModel.setGroups(List.of());
            setStatus("No packed cache found — nothing to reload.");
            return;
        }

        for (String id : SpriteCache.getIds()) {
            spriteList.add(new PackSprite(id, id, null, SpriteCache.getSprite(id)));
        }
        tableModel.fireTableDataChanged();
        refreshAnimations();
        setStatus("Reloaded " + spriteList.size() + " sprite(s) from pack.");
        if (!spriteList.isEmpty()) table.selectAll();
    }

    // =========================================================================
    //  Import helpers
    // =========================================================================

    /**
     * Recursively imports all {@code .png} files found under {@code root}.
     *
     * <p>Each file's sprite ID is the path relative to {@code root} with the
     * extension stripped and back-slashes normalised to forward slashes.
     *
     * <p>Example: root = {@code enemy/}, file = {@code enemy/goblin/walk/north/frame_0.png}
     * → ID = {@code goblin/walk/north/frame_0}
     *
     * @return number of sprites added or updated
     */
    private int importFolder(Path root) {
        int count = 0;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                if (!file.getFileName().toString().toLowerCase().endsWith(".png")) continue;
                String relPath = root.relativize(file).toString().replace('\\', '/');
                String id      = stripExtension(relPath);
                if (addOrReplace(id, relPath, file)) count++;
            }
        } catch (IOException ex) {
            setStatus("ERROR reading folder: " + ex.getMessage());
        }
        return count;
    }

    /**
     * Adds a new sprite entry or replaces an existing entry with the same ID.
     *
     * @param id          sprite lookup key
     * @param displayPath text shown in the Source column
     * @param file        absolute path to the PNG on disk
     * @return {@code true} if the image was read successfully
     */
    private boolean addOrReplace(String id, String displayPath, Path file) {
        BufferedImage img;
        try {
            img = ImageIO.read(file.toFile());
            if (img == null) return false;
        } catch (IOException ex) {
            LOGGER.warning("[Packer] Could not read " + file + ": " + ex.getMessage());
            return false;
        }

        PackSprite sprite = new PackSprite(id, displayPath, file, img);
        for (int i = 0; i < spriteList.size(); i++) {
            if (spriteList.get(i).id.equals(id)) {
                tableModel.replace(i, sprite);
                return true;
            }
        }
        tableModel.add(sprite);
        return true;
    }

    // =========================================================================
    //  Animation detection
    // =========================================================================

    /**
     * Re-analyses all loaded sprite IDs and rebuilds the "Detected Animations" table.
     * Should be called after any import, remove, or reload operation.
     */
    private void refreshAnimations() {
        animTableModel.setGroups(detectAnimations());
    }

    /**
     * Scans every loaded sprite's ID path and groups sprites by
     * {@code entity × animationType × direction}, with frames sorted
     * numerically within each group.
     *
     * <p>Only sprites whose IDs contain a recognised animation keyword are included;
     * flat item / UI sprites are silently skipped.
     */
    private List<AnimationGroup> detectAnimations() {
        // Preserve insertion order so the table is stable across refreshes
        Map<String, AnimationGroup> map = new LinkedHashMap<>();

        for (PackSprite sprite : spriteList) {
            DetectedPath dp = PathParser.parse(sprite.id);
            if (dp.animType() == null) continue;  // not a recognised animation path

            String key = dp.entity() + "|" + dp.animType() + "|" + dp.direction();
            map.computeIfAbsent(key, k -> new AnimationGroup(dp.entity(), dp.animType(), dp.direction()))
               .addFrame(sprite, dp.frameNum());
        }

        List<AnimationGroup> result = new ArrayList<>(map.values());
        result.forEach(AnimationGroup::sortFrames);
        return result;
    }

    /**
     * When the user clicks a row in the animation table, selects all corresponding
     * sprite rows in the sprite table above so they can be inspected or exported.
     */
    private void syncSpriteSelectionFromAnim() {
        int[] animRows = animTable.getSelectedRows();
        if (animRows.length == 0) return;

        // Collect all sprites that belong to any selected animation group
        Set<PackSprite> wanted = new LinkedHashSet<>();
        for (int animRow : animRows) {
            wanted.addAll(animTableModel.get(animRow).sortedSprites());
        }

        table.clearSelection();
        for (int row = 0; row < spriteList.size(); row++) {
            if (wanted.contains(spriteList.get(row))) {
                table.addRowSelectionInterval(row, row);
            }
        }

        // Scroll to the first selected sprite row
        if (!table.getSelectionModel().isSelectionEmpty()) {
            int first = table.getSelectedRows()[0];
            table.scrollRectToVisible(table.getCellRect(first, 0, true));
        }
    }

    // =========================================================================
    //  Export helpers
    // =========================================================================

    private void exportSprite(PackSprite s, Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            ImageIO.write(s.image, "PNG", dest.toFile());
        } catch (IOException ex) {
            setStatus("ERROR exporting '" + s.id + "': " + ex.getMessage());
        }
    }

    // =========================================================================
    //  Preview
    // =========================================================================

    private void updatePreview() {
        int row = table.getSelectedRow();
        if (row < 0) {
            previewLabel.setIcon(null);
            previewLabel.setText("(none)");
            return;
        }
        PackSprite s = tableModel.get(row);
        if (!ensureImageLoaded(s)) {
            previewLabel.setIcon(null);
            previewLabel.setText("(no image)");
            return;
        }
        previewLabel.setText(null);
        previewLabel.setIcon(scaledIcon(s.image, 220, 220));
    }

    private void showPreviewDialog() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        PackSprite s = tableModel.get(row);
        if (!ensureImageLoaded(s)) return;
        JDialog dlg = new JDialog(this, s.id, true);
        JLabel  lbl = new JLabel(new ImageIcon(s.image));
        lbl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dlg.add(lbl);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private boolean ensureImageLoaded(PackSprite s) {
        if (s.image != null) return true;
        if (s.sourcePath == null) return false;
        try {
            s.image = ImageIO.read(s.sourcePath.toFile());
            return s.image != null;
        } catch (IOException ex) {
            return false;
        }
    }

    /** Selects the rows from {@code firstRow} to the current end of the table. */
    private void selectNewRows(int firstRow) {
        int last = tableModel.size() - 1;
        if (last >= firstRow) {
            table.setRowSelectionInterval(firstRow, last);
            table.scrollRectToVisible(table.getCellRect(firstRow, 0, true));
        }
    }

    private static ImageIcon scaledIcon(BufferedImage img, int maxW, int maxH) {
        double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
        if (scale >= 1.0) return new ImageIcon(img);
        int w = (int)(img.getWidth()  * scale);
        int h = (int)(img.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return new ImageIcon(scaled);
    }

    private static String stripExtension(String path) {
        int dot   = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (dot > slash) ? path.substring(0, dot) : path;
    }

    private static JFileChooser newPngChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        return chooser;
    }

    /** Returns the output directory, creating it if necessary, or {@code null} on failure. */
    private static Path resolveOutputDir() {
        Path userCache = Paths.get(CacheConfig.CACHE_DIR);
        if (Files.isDirectory(userCache)) return userCache;

        Path projectCache = Paths.get("cache").toAbsolutePath();
        try {
            Files.createDirectories(projectCache);
            return projectCache;
        } catch (IOException ex) {
            LOGGER.warning("[Packer] Could not create output dir: " + ex.getMessage());
            return null;
        }
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" " + msg));
    }
}
