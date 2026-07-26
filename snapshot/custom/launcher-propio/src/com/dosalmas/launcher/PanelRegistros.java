package com.dosalmas.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Consola en vivo de la partida en curso, mas el listado de registros
 * guardados en disco (los que escribe {@link Registro}).
 *
 * Todo el texto se muestra con el mismo motor de coloreado por nivel
 * (INFO/AVISO/ERROR/otros), tanto si viene de la partida en marcha como si
 * viene de un archivo antiguo. El documento de Swing se construye siempre en
 * un hilo aparte y se cambia de un golpe en el hilo de interfaz
 * (JTextPane.setStyledDocument), que es muchisimo mas barato que insertar
 * miles de lineas una a una: el ultimo arranque real escribio 18490 lineas.
 */
public final class PanelRegistros extends JPanel {

    private static final int MAX_LINEAS_VIVO = 5000;
    private static final int INTERVALO_LOTE_MS = 150;

    // Lo que escriben los mods de Minecraft: [hh:mm:ss] [hilo/NIVEL] ...
    private static final Pattern PATRON_NIVEL =
            Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s*\\[[^/\\]]+/(\\w+)\\]");

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private enum Nivel { ERROR, AVISO, INFO, OTRO }

    private static final class Linea {
        final String texto;
        final Nivel nivel;
        Linea(String texto, Nivel nivel) { this.texto = texto; this.nivel = nivel; }
    }

    /** Item fijo de la lista de la izquierda: la sesion en curso. */
    private static final Object ITEM_VIVO = new Object();

    private static final class EntradaArchivo {
        final Path ruta;
        final long tamanoBytes;
        final FileTime fecha;
        EntradaArchivo(Path ruta, long tamanoBytes, FileTime fecha) {
            this.ruta = ruta; this.tamanoBytes = tamanoBytes; this.fecha = fecha;
        }
    }

    // ------------------------------------------------------------- Estado

    private final Deque<Linea> lineasVivo = new ArrayDeque<>();
    private final List<String> pendientesVivo = Collections.synchronizedList(new ArrayList<>());
    private Nivel nivelQueArrastra = Nivel.INFO;

    private boolean vistaEsVivo = true;
    private List<Linea> lineasArchivoActual = List.of();
    private Path archivoActual;

    private boolean siguiendoAlFinal = true;
    private int nuevasSinMostrar = 0;

    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "panel-registros-io");
        t.setDaemon(true);
        return t;
    });
    private final Timer temporizadorLote;
    private long peticionRenderId = 0;

    // --------------------------------------------------------------- UI

    private Tema.CampoTexto campoFiltro;
    private JCheckBox chkInfo, chkAviso, chkError, chkOtro;
    private JTextPane areaTexto;
    private DefaultListModel<Object> modeloLista;
    private JList<Object> listaArchivos;
    private JLabel etiquetaFuente;
    private Tema.BotonSecundario botonSeguirFinal;
    private Tema.BotonSecundario botonLimpiar;

    public PanelRegistros(Config config) {
        setOpaque(false);
        setLayout(new BorderLayout(Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO));
        setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));

        add(construirCabecera(), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);

        temporizadorLote = new Timer(INTERVALO_LOTE_MS, e -> volcarLoteVivo());
        temporizadorLote.start();

        mostrarVivo();
        cargarListaArchivos();
    }

    // ----------------------------------------------------------- Cabecera

    private JPanel construirCabecera() {
        JLabel titulo = new JLabel("Registros");
        titulo.setFont(Tema.titulo());
        titulo.setForeground(Tema.TEXTO);

        JLabel subtitulo = new JLabel("Consola en vivo de la partida y registros guardados");
        subtitulo.setFont(Tema.cuerpo());
        subtitulo.setForeground(Tema.TEXTO_SUAVE);

        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        textos.add(titulo);
        textos.add(Box.createVerticalStrut(2));
        textos.add(subtitulo);

        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(textos, BorderLayout.WEST);
        envoltorio.setBorder(new EmptyBorder(0, 0, Tema.ESPACIO_PEQUENO, 0));
        return envoltorio;
    }

    // ------------------------------------------------------------- Cuerpo

    private JSplitPane construirCuerpo() {
        JPanel izquierda = construirListaArchivos();
        JPanel derecha = construirVisor();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, derecha);
        split.setOpaque(false);
        split.setDividerSize(8);
        split.setResizeWeight(0.0);
        split.setContinuousLayout(true);
        split.setDividerLocation(230);
        // setUI() ANTES de setBorder(null): BasicSplitPaneUI.installUI() ->
        // installDefaults() vuelve a poner 'SplitPane.border' (el highlight
        // blanco de Metal en el borde derecho e inferior) cada vez que se
        // instala una UI nueva. Si setBorder(null) se llama antes, ese
        // installDefaults lo pisa igual; llamandolo despues es lo que manda.
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override public void paint(java.awt.Graphics g) {
                        g.setColor(Tema.FONDO);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        split.setBorder(null);
        return split;
    }

    private JPanel construirListaArchivos() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_PEQUENO));
        tarjeta.setBorder(new EmptyBorder(Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO));
        tarjeta.setPreferredSize(new Dimension(230, 10));
        tarjeta.setMinimumSize(new Dimension(190, 10));

        JLabel etiqueta = new JLabel("SESIONES");
        etiqueta.setFont(Tema.etiqueta());
        etiqueta.setForeground(Tema.TEXTO_DEBIL);
        tarjeta.add(etiqueta, BorderLayout.NORTH);

        modeloLista = new DefaultListModel<>();
        listaArchivos = new JList<>(modeloLista);
        listaArchivos.setOpaque(false);
        listaArchivos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaArchivos.setFixedCellHeight(46);
        listaArchivos.setCellRenderer(new RenderizadorArchivo());
        listaArchivos.setSelectionBackground(Tema.ACENTO_APAGADO);
        listaArchivos.setSelectionForeground(Tema.TEXTO);
        listaArchivos.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Object sel = listaArchivos.getSelectedValue();
            if (sel == ITEM_VIVO) {
                mostrarVivo();
            } else if (sel instanceof EntradaArchivo) {
                mostrarArchivo(((EntradaArchivo) sel).ruta);
            }
        });
        JScrollPane scroll = new JScrollPane(listaArchivos,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        tarjeta.add(scroll, BorderLayout.CENTER);

        Tema.BotonSecundario abrirCarpeta = new Tema.BotonSecundario("Abrir carpeta");
        abrirCarpeta.setFont(Tema.etiqueta());
        abrirCarpeta.addActionListener(e -> abrirCarpetaLogs());
        JPanel pie = new JPanel(new BorderLayout());
        pie.setOpaque(false);
        pie.add(abrirCarpeta, BorderLayout.CENTER);
        tarjeta.add(pie, BorderLayout.SOUTH);

        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(tarjeta, BorderLayout.CENTER);
        return envoltorio;
    }

    private final class RenderizadorArchivo extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            JPanel fila = new JPanel();
            fila.setLayout(new BoxLayout(fila, BoxLayout.Y_AXIS));
            fila.setOpaque(true);
            fila.setBackground(isSelected ? Tema.ACENTO_APAGADO : Tema.SUPERFICIE);
            fila.setBorder(new EmptyBorder(6, Tema.ESPACIO_PEQUENO, 6, Tema.ESPACIO_PEQUENO));

            JLabel principal = new JLabel();
            principal.setFont(Tema.cuerpoNegrita());
            principal.setForeground(Tema.TEXTO);
            JLabel secundario = new JLabel();
            secundario.setFont(Tema.etiqueta());
            secundario.setForeground(Tema.TEXTO_SUAVE);

            if (value == ITEM_VIVO) {
                principal.setText("Consola en vivo");
                secundario.setText(vistaEsVivo ? "sesion actual" : "sin partida activa");
            } else if (value instanceof EntradaArchivo) {
                EntradaArchivo a = (EntradaArchivo) value;
                String fecha = FORMATO_FECHA.format(Instant.ofEpochMilli(a.fecha.toMillis()).atZone(ZoneId.systemDefault()));
                principal.setText(fecha);
                secundario.setText(formatoTamano(a.tamanoBytes));
            }
            fila.add(principal);
            fila.add(secundario);
            return fila;
        }
    }

    private static String formatoTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // --------------------------------------------------------------- Visor

    private JPanel construirVisor() {
        JPanel panel = new JPanel(new BorderLayout(0, Tema.ESPACIO_PEQUENO));
        panel.setOpaque(false);

        // El filtro y el estado van en filas separadas: si compartieran una
        // sola fila con los botones, un ancho ajustado los hace solaparse
        // (FlowLayout no reparte alto extra cuando envuelve a una fila mas).
        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        JPanel filtros = construirBarraFiltros();
        filtros.setAlignmentX(Component.LEFT_ALIGNMENT);
        cabecera.add(filtros);
        cabecera.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        etiquetaFuente = new JLabel(" ");
        etiquetaFuente.setFont(Tema.etiqueta());
        etiquetaFuente.setForeground(Tema.TEXTO_DEBIL);
        etiquetaFuente.setAlignmentX(Component.LEFT_ALIGNMENT);
        cabecera.add(etiquetaFuente);
        panel.add(cabecera, BorderLayout.NORTH);

        Tema.Tarjeta tarjetaTexto = new Tema.Tarjeta(new BorderLayout());
        tarjetaTexto.setBorder(new EmptyBorder(2, 2, 2, 2));

        areaTexto = new JTextPane() {
            @Override public boolean getScrollableTracksViewportWidth() {
                Component padre = getParent();
                if (padre == null) return true;
                return getUI().getPreferredSize(this).width <= padre.getSize().width;
            }
        };
        areaTexto.setEditable(false);
        areaTexto.setFont(Tema.mono());
        areaTexto.setBackground(Tema.SUPERFICIE);
        areaTexto.setForeground(Tema.TEXTO);
        areaTexto.setSelectionColor(Tema.ACENTO_APAGADO);
        areaTexto.setSelectedTextColor(Tema.TEXTO);
        areaTexto.setBorder(new EmptyBorder(Tema.ESPACIO_PEQUENO, Tema.ESPACIO_MEDIO, Tema.ESPACIO_PEQUENO, Tema.ESPACIO_MEDIO));
        areaTexto.setStyledDocument(construirDocumentoVacio("Sin partidas todavia. Pulsa JUGAR para ver aqui la consola."));
        // Sin esto, el caret por defecto queda al FINAL del texto insertado
        // (asi actualiza Swing el caret tras un insertString hecho directo
        // sobre el documento) y la vista se autodesplaza para mantenerlo
        // visible: cuando el mensaje no cabe entero en un visor angosto, se
        // ve recortado por la izquierda ("in partidas todavia" en vez de
        // "Sin partidas todavia"). El resto del flujo (aplicarDocumento) ya
        // fija el caret con criterio; esta era la unica llamada directa que
        // no lo hacia.
        areaTexto.setCaretPosition(0);

        // Si el usuario sube a leer, se deja de seguir el final hasta que
        // pida volver: nadie quiere que la consola le arranque la lectura.
        // OJO: solo se comprueba tras un gesto real del usuario (rueda o
        // arrastre de la barra), nunca tras un AdjustmentEvent generico: esos
        // tambien los dispara el propio panel al reconstruir el documento y
        // desactivarian el seguimiento en cada lote sin que nadie tocara nada.
        areaTexto.addMouseWheelListener(e -> {
            if (vistaEsVivo) SwingUtilities.invokeLater(this::marcarSeguimientoSegunScroll);
        });

        JScrollPane scroll = new JScrollPane(areaTexto,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        java.awt.event.MouseAdapter gestoManual = new java.awt.event.MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                if (vistaEsVivo) SwingUtilities.invokeLater(PanelRegistros.this::marcarSeguimientoSegunScroll);
            }
        };
        scroll.getVerticalScrollBar().addMouseListener(gestoManual);
        tarjetaTexto.add(scroll, BorderLayout.CENTER);

        panel.add(tarjetaTexto, BorderLayout.CENTER);
        panel.add(construirBarraInferior(), BorderLayout.SOUTH);
        return panel;
    }

    private void marcarSeguimientoSegunScroll() {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, areaTexto);
        if (scroll == null) return;
        var barra = scroll.getVerticalScrollBar();
        boolean alFinal = !barra.isVisible()
                || (barra.getValue() + barra.getVisibleAmount() >= barra.getMaximum() - 4);
        siguiendoAlFinal = alFinal;
        if (siguiendoAlFinal) nuevasSinMostrar = 0;
        actualizarBotonSeguir();
    }

    private JPanel construirBarraFiltros() {
        // FilaFluida, no BoxLayout X_AXIS: con BoxLayout, cuando la suma de
        // anchos preferidos no cabe, el campo de filtro (el unico hijo
        // elastico, sin maximo fijo real de forma efectiva) se comprimia
        // hasta un munon de ~20 px en vez de que la barra envolviera a una
        // segunda fila. A 900x600 -- el minimo que este launcher declara --
        // eso recortaba "Buscar en el registro..." y el boton "Buscar el
        // error" contra el borde de la ventana.
        FilaFluida barra = new FilaFluida(FlowLayout.LEFT, Tema.ESPACIO_MEDIO, Tema.ESPACIO_XS);

        campoFiltro = new Tema.CampoTexto(16);
        campoFiltro.setSugerencia("Buscar en el registro...");
        campoFiltro.setPreferredSize(new Dimension(200, 34));
        campoFiltro.setMinimumSize(new Dimension(160, 34));
        campoFiltro.setMaximumSize(new Dimension(240, 34));
        campoFiltro.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onFiltroCambiado(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onFiltroCambiado(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onFiltroCambiado(); }
        });
        barra.add(campoFiltro);

        chkInfo = crearCheckNivel("Info", Tema.TEXTO_SUAVE);
        chkAviso = crearCheckNivel("Aviso", Tema.AVISO);
        chkError = crearCheckNivel("Error", Tema.PELIGRO);
        chkOtro = crearCheckNivel("Otros", Tema.TEXTO_DEBIL);
        barra.add(chkInfo);
        barra.add(chkAviso);
        barra.add(chkError);
        barra.add(chkOtro);

        Tema.BotonSecundario buscarError = new Tema.BotonSecundario("Buscar el error");
        buscarError.addActionListener(e -> buscarError());
        barra.add(buscarError);

        return barra;
    }

    private JCheckBox crearCheckNivel(String texto, Color color) {
        JCheckBox c = new JCheckBox(texto, true);
        c.setOpaque(false);
        c.setFont(Tema.etiqueta());
        c.setForeground(color);
        c.setFocusPainted(false);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addActionListener(e -> onFiltroCambiado());
        return c;
    }

    private JPanel construirBarraInferior() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);

        // Igual que en construirBarraFiltros: un FlowLayout normal no envuelve,
        // solo recorta. A 900x600 "Siguiendo el final" quedaba cortado por la
        // izquierda y "Limpiar" por la derecha.
        FilaFluida botones = new FilaFluida(FlowLayout.RIGHT, Tema.ESPACIO_PEQUENO, Tema.ESPACIO_XS);

        botonSeguirFinal = new Tema.BotonSecundario("Ir al final");
        botonSeguirFinal.addActionListener(e -> irAlFinal());

        Tema.BotonSecundario copiar = new Tema.BotonSecundario("Copiar");
        copiar.addActionListener(e -> copiarVisible());

        Tema.BotonSecundario guardar = new Tema.BotonSecundario("Guardar como...");
        guardar.addActionListener(e -> guardarComo());

        botonLimpiar = new Tema.BotonSecundario("Limpiar");
        botonLimpiar.addActionListener(e -> limpiarConsola());

        botones.add(botonSeguirFinal);
        botones.add(copiar);
        botones.add(guardar);
        botones.add(botonLimpiar);
        barra.add(botones, BorderLayout.CENTER);
        return barra;
    }

    /**
     * Fila de componentes que envuelve a una segunda linea si no caben, en vez
     * de recortarse: FlowLayout.getPreferredSize() siempre calcula como si
     * fuera una sola fila, sin importar el ancho real disponible. Aqui se
     * recalcula la altura mirando el ancho ya asignado al panel padre (mismo
     * truco que PanelAjustes.FilaFluida).
     */
    private static final class FilaFluida extends JPanel {
        FilaFluida(int alineacion, int hgap, int vgap) {
            super(new FlowLayout(alineacion, hgap, vgap));
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            Container padre = getParent();
            int ancho = (padre != null) ? padre.getWidth() : 0;
            if (ancho <= 0) return super.getPreferredSize();

            FlowLayout fl = (FlowLayout) getLayout();
            int hgap = fl.getHgap();
            int vgap = fl.getVgap();
            int x = 0;
            int alturaFila = 0;
            int alturaTotal = 0;
            boolean filaConAlgo = false;
            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (filaConAlgo && x + hgap + d.width > ancho) {
                    alturaTotal += alturaFila + vgap;
                    x = 0;
                    alturaFila = 0;
                    filaConAlgo = false;
                }
                x += (filaConAlgo ? hgap : 0) + d.width;
                alturaFila = Math.max(alturaFila, d.height);
                filaConAlgo = true;
            }
            alturaTotal += alturaFila;
            return new Dimension(ancho, alturaTotal);
        }
    }

    // -------------------------------------------------------- API publica

    /** Anade una linea de la partida en curso. Puede llamarse desde cualquier hilo. */
    public void anexarLinea(String linea) {
        pendientesVivo.add(linea);
    }

    /** Vacia la consola en vivo (nueva partida). Puede llamarse desde cualquier hilo. */
    public void limpiarConsola() {
        Runnable accion = () -> {
            lineasVivo.clear();
            pendientesVivo.clear();
            nivelQueArrastra = Nivel.INFO;
            nuevasSinMostrar = 0;
            siguiendoAlFinal = true;
            if (vistaEsVivo) renderizar(new ArrayList<>(lineasVivo), true);
            actualizarBotonSeguir();
        };
        if (SwingUtilities.isEventDispatchThread()) accion.run();
        else SwingUtilities.invokeLater(accion);
    }

    /** Vuelve a listar los registros guardados en disco. */
    public void refrescar() {
        cargarListaArchivos();
    }

    // --------------------------------------------------------- Vivo: lotes

    private void volcarLoteVivo() {
        List<String> lote;
        synchronized (pendientesVivo) {
            if (pendientesVivo.isEmpty()) return;
            lote = new ArrayList<>(pendientesVivo);
            pendientesVivo.clear();
        }
        for (String texto : lote) {
            Nivel explicito = detectarNivel(texto);
            Nivel nivel = (explicito != null) ? explicito : nivelQueArrastra;
            if (explicito != null) nivelQueArrastra = explicito;
            lineasVivo.addLast(new Linea(texto, nivel));
        }
        while (lineasVivo.size() > MAX_LINEAS_VIVO) lineasVivo.removeFirst();

        if (!vistaEsVivo) return;
        if (siguiendoAlFinal) {
            renderizar(new ArrayList<>(lineasVivo), true);
        } else {
            nuevasSinMostrar += lote.size();
            actualizarBotonSeguir();
        }
    }

    private static Nivel detectarNivel(String linea) {
        Matcher m = PATRON_NIVEL.matcher(linea);
        if (!m.find()) return null;
        String n = m.group(1).toUpperCase(Locale.ROOT);
        switch (n) {
            case "ERROR": case "FATAL": return Nivel.ERROR;
            case "WARN": case "WARNING": return Nivel.AVISO;
            case "INFO": return Nivel.INFO;
            default: return Nivel.OTRO;
        }
    }

    // ------------------------------------------------------- Cambio de vista

    private void mostrarVivo() {
        vistaEsVivo = true;
        archivoActual = null;
        botonSeguirFinal.setVisible(true);
        botonLimpiar.setEnabled(true);
        botonLimpiar.setToolTipText(null);
        siguiendoAlFinal = true;
        nuevasSinMostrar = 0;
        listaArchivos.setSelectedValue(ITEM_VIVO, false);
        renderizar(new ArrayList<>(lineasVivo), true);
        actualizarBotonSeguir();
    }

    private void mostrarArchivo(Path ruta) {
        vistaEsVivo = false;
        archivoActual = ruta;
        botonSeguirFinal.setVisible(true);
        botonLimpiar.setEnabled(false);
        botonLimpiar.setToolTipText("Solo se puede limpiar la consola en vivo");
        actualizarBotonSeguir();
        etiquetaFuente.setText("Cargando " + ruta.getFileName() + "...");
        areaTexto.setStyledDocument(construirDocumentoVacio("Cargando registro..."));
        areaTexto.setCaretPosition(0);

        ejecutor.submit(() -> {
            List<Linea> lineas = leerArchivoComoLineas(ruta);
            SwingUtilities.invokeLater(() -> {
                if (archivoActual != ruta) return; // el usuario ya cambio de seleccion
                lineasArchivoActual = lineas;
                renderizar(lineas, false);
            });
        });
    }

    private List<Linea> leerArchivoComoLineas(Path ruta) {
        List<Linea> out = new ArrayList<>();
        Nivel arrastre = Nivel.INFO;
        try (var stream = Files.lines(ruta, StandardCharsets.UTF_8)) {
            for (String texto : (Iterable<String>) stream::iterator) {
                Nivel explicito = detectarNivel(texto);
                Nivel nivel = (explicito != null) ? explicito : arrastre;
                if (explicito != null) arrastre = explicito;
                out.add(new Linea(texto, nivel));
            }
        } catch (IOException e) {
            out.add(new Linea("No se pudo leer el archivo: " + e.getMessage(), Nivel.ERROR));
        }
        return out;
    }

    // ----------------------------------------------------------- Filtros

    private void onFiltroCambiado() {
        List<Linea> fuente = vistaEsVivo ? new ArrayList<>(lineasVivo) : lineasArchivoActual;
        renderizar(fuente, vistaEsVivo && siguiendoAlFinal);
    }

    // ------------------------------------------------------ Construccion

    /**
     * Reconstruye el documento mostrado a partir de una lista de lineas,
     * respetando el filtro y las casillas de nivel. Se hace en un hilo aparte
     * y se aplica de un golpe: es la unica forma de que miles de lineas no
     * congelen la interfaz.
     */
    private void renderizar(List<Linea> fuente, boolean irAlFinal) {
        String filtro = campoFiltro.getText().trim().toLowerCase(Locale.ROOT);
        boolean visInfo = chkInfo.isSelected();
        boolean visAviso = chkAviso.isSelected();
        boolean visError = chkError.isSelected();
        boolean visOtro = chkOtro.isSelected();

        long miId = ++peticionRenderId;
        ejecutor.submit(() -> {
            DefaultStyledDocument doc = new DefaultStyledDocument();
            int total = 0;
            try {
                for (Linea l : fuente) {
                    boolean visibleNivel;
                    switch (l.nivel) {
                        case INFO: visibleNivel = visInfo; break;
                        case AVISO: visibleNivel = visAviso; break;
                        case ERROR: visibleNivel = visError; break;
                        default: visibleNivel = visOtro; break;
                    }
                    if (!visibleNivel) continue;
                    if (!filtro.isEmpty() && !l.texto.toLowerCase(Locale.ROOT).contains(filtro)) continue;

                    SimpleAttributeSet estilo = estiloDe(l.nivel);
                    doc.insertString(doc.getLength(), l.texto + "\n", estilo);
                    total++;
                }
            } catch (BadLocationException e) {
                // no deberia pasar insertando siempre al final
            }
            int totalFinal = total;
            SwingUtilities.invokeLater(() -> {
                if (miId != peticionRenderId) return; // ya hay una peticion mas nueva
                aplicarDocumento(doc, totalFinal, irAlFinal);
            });
        });
    }

    private void aplicarDocumento(DefaultStyledDocument doc, int totalLineas, boolean irAlFinal) {
        if (totalLineas == 0) {
            try {
                doc.insertString(0, mensajeVacio(), estiloDe(Nivel.OTRO));
            } catch (BadLocationException ignored) { }
        }
        areaTexto.setStyledDocument(doc);
        // "Ir al final" solo tiene sentido con lineas reales (seguir la
        // consola en vivo). Con un mensaje vacio (p.ej. "Sin partidas
        // todavia...") irAlFinal llega en true igualmente (mostrarVivo() lo
        // pasa siempre asi) y mandaba el caret al final del propio mensaje:
        // si el visor es angosto y el mensaje no cabe entero, eso recorta el
        // principio ("in partidas todavia" en vez de "Sin partidas
        // todavia"). Sin contenido real, el caret siempre va a 0.
        if (irAlFinal && totalLineas > 0) {
            areaTexto.setCaretPosition(doc.getLength());
        } else {
            areaTexto.setCaretPosition(0);
        }
        actualizarEtiquetaLineas(totalLineas);
    }

    private String mensajeVacio() {
        if (!campoFiltro.getText().isBlank()) return "Ningun resultado para \"" + campoFiltro.getText().trim() + "\".";
        if (vistaEsVivo && lineasVivo.isEmpty()) return "Sin partidas todavia. Pulsa JUGAR para ver aqui la consola.";
        if (vistaEsVivo) return "Todas las lineas estan ocultas por los filtros de nivel.";
        return "El archivo esta vacio o todas sus lineas estan ocultas por los filtros.";
    }

    private DefaultStyledDocument construirDocumentoVacio(String texto) {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        try {
            doc.insertString(0, texto, estiloDe(Nivel.OTRO));
        } catch (BadLocationException ignored) { }
        return doc;
    }

    private static SimpleAttributeSet estiloDe(Nivel nivel) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        switch (nivel) {
            case ERROR: StyleConstants.setForeground(a, Tema.PELIGRO); break;
            case AVISO: StyleConstants.setForeground(a, Tema.AVISO); break;
            case INFO:  StyleConstants.setForeground(a, Tema.TEXTO); break;
            default:    StyleConstants.setForeground(a, Tema.TEXTO_SUAVE); break;
        }
        return a;
    }

    private void actualizarEtiquetaLineas(int totalVisibles) {
        String base = vistaEsVivo
                ? "Consola en vivo"
                : (archivoActual != null ? archivoActual.getFileName().toString() : "");
        etiquetaFuente.setText(base + " -- " + totalVisibles + " lineas visibles");
    }

    private void actualizarBotonSeguir() {
        if (!vistaEsVivo) {
            botonSeguirFinal.setText("Ir al final");
            botonSeguirFinal.setForeground(Tema.TEXTO);
            return;
        }
        if (siguiendoAlFinal) {
            botonSeguirFinal.setText("Siguiendo el final");
            botonSeguirFinal.setForeground(Tema.TEXTO_SUAVE);
        } else {
            String extra = (nuevasSinMostrar > 0) ? " (" + nuevasSinMostrar + " nuevas)" : "";
            botonSeguirFinal.setText("Ir al final" + extra);
            botonSeguirFinal.setForeground(Tema.ACENTO_FUERTE);
        }
    }

    private void irAlFinal() {
        siguiendoAlFinal = true;
        nuevasSinMostrar = 0;
        List<Linea> fuente = vistaEsVivo ? new ArrayList<>(lineasVivo) : lineasArchivoActual;
        renderizar(fuente, true);
        actualizarBotonSeguir();
    }

    // ------------------------------------------------------- Buscar error

    private static boolean pareceError(Linea l) {
        return l.nivel == Nivel.ERROR
                || l.texto.contains("Exception")
                || l.texto.contains("Caused by");
    }

    private void buscarError() {
        List<Linea> fuente = vistaEsVivo ? new ArrayList<>(lineasVivo) : lineasArchivoActual;
        int idx = -1;
        for (int i = 0; i < fuente.size(); i++) {
            if (pareceError(fuente.get(i))) { idx = i; break; }
        }
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "No se encontro ningun error en este registro.",
                    "Buscar el error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Para que la linea sea visible hace falta quitar cualquier filtro
        // que la estuviera ocultando.
        campoFiltro.setText("");
        chkInfo.setSelected(true);
        chkAviso.setSelected(true);
        chkError.setSelected(true);
        chkOtro.setSelected(true);
        siguiendoAlFinal = false;

        final int lineaObjetivo = idx;
        long miId = ++peticionRenderId;
        List<Linea> copia = new ArrayList<>(fuente);
        ejecutor.submit(() -> {
            DefaultStyledDocument doc = new DefaultStyledDocument();
            int offsetObjetivo = -1;
            int longitudObjetivo = 0;
            try {
                for (int i = 0; i < copia.size(); i++) {
                    Linea l = copia.get(i);
                    int inicio = doc.getLength();
                    String textoLinea = l.texto + "\n";
                    doc.insertString(doc.getLength(), textoLinea, estiloDe(l.nivel));
                    if (i == lineaObjetivo) {
                        offsetObjetivo = inicio;
                        longitudObjetivo = textoLinea.length() - 1;
                    }
                }
            } catch (BadLocationException e) {
                // no deberia pasar
            }
            int offsetFinal = offsetObjetivo;
            int longFinal = longitudObjetivo;
            int totalFinal = copia.size();
            SwingUtilities.invokeLater(() -> {
                if (miId != peticionRenderId) return;
                aplicarDocumento(doc, totalFinal, false);
                actualizarBotonSeguir();
                if (offsetFinal >= 0) {
                    areaTexto.setCaretPosition(offsetFinal);
                    areaTexto.select(offsetFinal, offsetFinal + longFinal);
                    try {
                        java.awt.geom.Rectangle2D r2 = areaTexto.modelToView2D(offsetFinal);
                        if (r2 != null) {
                            Rectangle r = r2.getBounds();
                            r.y = Math.max(0, r.y - 120);
                            r.height = 260;
                            areaTexto.scrollRectToVisible(r);
                        }
                    } catch (BadLocationException ignored) { }
                }
            });
        });
    }

    // ------------------------------------------------------------ Acciones

    private void copiarVisible() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(areaTexto.getText()), null);
    }

    private void guardarComo() {
        JFileChooser chooser = new JFileChooser();
        String sugerido = vistaEsVivo
                ? "consola-en-vivo.txt"
                : (archivoActual != null ? archivoActual.getFileName().toString() : "registro.txt");
        chooser.setSelectedFile(new File(sugerido));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(chooser.getSelectedFile().toPath(), areaTexto.getText(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo:\n" + e.getMessage(),
                        "Guardar como", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void abrirCarpetaLogs() {
        Path dir = Rutas.carpetaLogs();
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (Exception ignored) {
            // se cae al mensaje con la ruta
        }
        JOptionPane.showMessageDialog(this, "Abre esta carpeta a mano:\n" + dir,
                "Carpeta de registros", JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------- Lista de disco

    private void cargarListaArchivos() {
        ejecutor.submit(() -> {
            List<EntradaArchivo> encontrados = new ArrayList<>();
            Path dir = Rutas.carpetaLogs();
            if (Files.isDirectory(dir)) {
                try (var s = Files.list(dir)) {
                    for (Path f : (Iterable<Path>) s::iterator) {
                        String n = f.getFileName().toString();
                        if (!n.startsWith("launcher-") || !n.endsWith(".log")) continue;
                        try {
                            encontrados.add(new EntradaArchivo(f, Files.size(f), Files.getLastModifiedTime(f)));
                        } catch (IOException ignored) {
                            // archivo que desaparecio entre el listado y el stat: se ignora
                        }
                    }
                } catch (IOException ignored) {
                    // carpeta sin permisos o inexistente todavia: lista vacia
                }
            }
            encontrados.sort((a, b) -> b.fecha.compareTo(a.fecha));
            SwingUtilities.invokeLater(() -> {
                Object seleccionPrevia = listaArchivos.getSelectedValue();
                Path rutaPrevia = (seleccionPrevia instanceof EntradaArchivo)
                        ? ((EntradaArchivo) seleccionPrevia).ruta : null;

                modeloLista.clear();
                modeloLista.addElement(ITEM_VIVO);
                for (EntradaArchivo e : encontrados) modeloLista.addElement(e);

                if (vistaEsVivo) {
                    listaArchivos.setSelectedValue(ITEM_VIVO, false);
                } else if (rutaPrevia != null) {
                    for (int i = 0; i < modeloLista.size(); i++) {
                        Object it = modeloLista.get(i);
                        if (it instanceof EntradaArchivo && ((EntradaArchivo) it).ruta.equals(rutaPrevia)) {
                            listaArchivos.setSelectedIndex(i);
                            break;
                        }
                    }
                }
            });
        });
    }
}
