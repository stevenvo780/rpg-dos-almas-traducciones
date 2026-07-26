package com.dosalmas.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Ventana principal del launcher.
 *
 * Dos modos sobre la MISMA ventana y los MISMOS paneles:
 *
 *  - Modo simple (Isa): no hay barra lateral ni secciones. Solo se ve
 *    PanelJugar dentro de una tarjeta centrada, en una ventana pequena.
 *  - Modo avanzado (Steven): aparece la barra lateral con las cinco secciones
 *    (Jugar, Mods, Apariencia, Ajustes, Registros) sobre un CardLayout, y la
 *    ventana crece.
 *
 * El cambio de modo lo decide PanelAjustes y llega por callback: no hace falta
 * reiniciar el launcher, la ventana se reconfigura en caliente.
 *
 * El lanzamiento del juego vive dentro de PanelJugar (verificacion previa con
 * Verificador, registro persistente con Registro, lectura de la salida con
 * publish/process y diagnostico de la salida anomala con Mensajes). Esta
 * ventana se encarga de lo que le corresponde: conectar esa consola con
 * PanelRegistros, dar el foco inicial al nombre, hacer que Enter equivalga a
 * JUGAR y no cerrarse en silencio con una partida viva.
 */
public final class LauncherWindow extends JFrame {

    /** Secciones navegables; en modo simple solo se usa JUGAR. */
    private enum Seccion {
        JUGAR("Jugar", "jugar"),
        MODS("Mods", "mods"),
        SKINS("Apariencia", "skins"),
        AJUSTES("Ajustes", "ajustes"),
        REGISTROS("Registros", "registros");

        final String etiqueta;
        final String tarjeta;

        Seccion(String etiqueta, String tarjeta) {
            this.etiqueta = etiqueta;
            this.tarjeta = tarjeta;
        }
    }

    private static final int ANCHO_LATERAL = 208;
    private static final int ANCHO_SIMPLE = 620;
    private static final Dimension MINIMO_SIMPLE   = new Dimension(470, 520);
    private static final Dimension TAMANO_AVANZADO = new Dimension(1160, 800);
    private static final Dimension MINIMO_AVANZADO = new Dimension(900, 600);

    private final Config config;

    private final PanelJugar panelJugar;
    private final PanelMods panelMods;
    private final PanelSkins panelSkins;
    private final PanelAjustes panelAjustes;
    private final PanelRegistros panelRegistros;

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final BarraLateral barraLateral;
    private final JPanel barraVolver;
    private final JLabel etiquetaJugador = new JLabel(" ");
    private JScrollPane scrollJugar;

    private Seccion seccionActual = Seccion.JUGAR;

    // Se localizan una vez dentro de PanelJugar para dar el foco inicial y para
    // que Enter dispare JUGAR, sin obligar a ese panel a exponer sus controles.
    private final Tema.CampoTexto campoNombre;
    private final Tema.BotonPrimario botonJugar;

    // ------------------------------------------------------------- Arranque

    /** Punto de entrada de la GUI: aplica el tema y abre la ventana en el EDT. */
    public static void abrir() {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            new LauncherWindow().setVisible(true);
        });
    }

    private LauncherWindow() {
        super("RPG Dos Almas -- Launcher");
        this.config = Config.load();

        // PanelRegistros primero: PanelJugar le entrega la consola del juego.
        panelRegistros = new PanelRegistros(config);

        panelJugar = new PanelJugar(
                config,
                this::alGuardarDesdeJugar,
                () -> mostrarSeccion(Seccion.AJUSTES),
                this::alLineaDeConsola);

        panelMods = new PanelMods(config, this::alCambiarMods);
        panelSkins = new PanelSkins(config);
        panelAjustes = new PanelAjustes(config, this::alGuardarConfig, this::alCambiarModo);
        // Ese panel se pinta opaco por su cuenta; dejandolo transparente hereda
        // el degradado del fondo igual que los demas.
        panelAjustes.setOpaque(false);

        barraLateral = new BarraLateral();
        barraVolver = construirBarraVolver();

        setIconImage(iconoVentana(64));
        setContentPane(construirContenido());

        campoNombre = buscar(panelJugar, Tema.CampoTexto.class);
        botonJugar = buscar(panelJugar, Tema.BotonPrimario.class);

        // Cerrar la ventana no debe dejar el juego huerfano ni matarlo por
        // sorpresa: se pregunta cuando sigue vivo.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { alCerrar(); }
        });

        aplicarModo(false);
        mostrarSeccion(Seccion.JUGAR);
        setLocationRelativeTo(null);

        // El foco inicial es para el nombre, no para la navegacion.
        SwingUtilities.invokeLater(() -> {
            if (campoNombre != null) {
                campoNombre.requestFocusInWindow();
                campoNombre.selectAll();
            }
            // Dar el foco hace que el JScrollPane desplace la vista hasta el
            // campo y corte la cabecera; se vuelve arriba una vez asentado.
            SwingUtilities.invokeLater(this::scrollJugarArriba);
        });
    }

    /** Deja la pantalla de jugar mostrando su parte superior. */
    private void scrollJugarArriba() {
        if (scrollJugar == null) return;
        scrollJugar.getViewport().setViewPosition(new java.awt.Point(0, 0));
    }

    // --------------------------------------------------------------- Layout

    private JPanel construirContenido() {
        Tema.PanelFondo raiz = new Tema.PanelFondo(new BorderLayout());

        contenido.setOpaque(false);
        contenido.add(construirTarjetaJugar(), Seccion.JUGAR.tarjeta);
        contenido.add(panelMods, Seccion.MODS.tarjeta);
        contenido.add(panelSkins, Seccion.SKINS.tarjeta);
        contenido.add(panelAjustes, Seccion.AJUSTES.tarjeta);
        contenido.add(panelRegistros, Seccion.REGISTROS.tarjeta);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.add(barraVolver, BorderLayout.NORTH);
        centro.add(contenido, BorderLayout.CENTER);

        raiz.add(barraLateral, BorderLayout.WEST);
        raiz.add(centro, BorderLayout.CENTER);
        return raiz;
    }

    /**
     * PanelJugar dentro de una tarjeta centrada. Es el mismo componente en los
     * dos modos: aqui solo se le pone marco y se le deja sitio para crecer;
     * cuando despliega los problemas de instalacion aparece el scroll en vez de
     * recortarse en silencio.
     */
    private JComponent construirTarjetaJugar() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout());
        tarjeta.setBorder(new EmptyBorder(Tema.ESPACIO_PEQUENO, Tema.ESPACIO_GRANDE,
                Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));
        tarjeta.add(panelJugar, BorderLayout.CENTER);

        PanelCentrador centrador = new PanelCentrador();
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.weighty = 1;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(Tema.ESPACIO_MEDIO, Tema.ESPACIO_GRANDE,
                Tema.ESPACIO_MEDIO, Tema.ESPACIO_GRANDE);
        centrador.add(tarjeta, gc);

        // Con scroll horizontal "cuando haga falta", no "nunca": si la ventana
        // se estrecha por debajo del ancho preferido de la tarjeta, es
        // preferible poder desplazarse a que Swing recurra a los tamanos
        // minimos y desmonte el panel (ver PanelCentrador).
        scrollJugar = new JScrollPane(centrador,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollJugar.setOpaque(false);
        scrollJugar.getViewport().setOpaque(false);
        scrollJugar.setBorder(null);
        scrollJugar.getVerticalScrollBar().setUnitIncrement(16);
        scrollJugar.getHorizontalScrollBar().setUnitIncrement(16);
        return scrollJugar;
    }

    /**
     * En modo simple no hay barra lateral, asi que cuando PanelJugar manda a
     * Ajustes hace falta una forma evidente de volver. Solo se ve en ese caso.
     */
    private JPanel construirBarraVolver() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        barra.setBorder(new EmptyBorder(Tema.ESPACIO_MEDIO, Tema.ESPACIO_GRANDE, 0, Tema.ESPACIO_GRANDE));
        Tema.BotonSecundario volver = new Tema.BotonSecundario("Volver a JUGAR");
        volver.addActionListener(e -> mostrarSeccion(Seccion.JUGAR));
        barra.add(volver, BorderLayout.WEST);
        barra.setVisible(false);
        return barra;
    }

    /**
     * Panel centrador del contenido de "Jugar".
     *
     * Se estira al ancho del viewport SOLO mientras quepa su ancho preferido.
     * Si se fuerza siempre (el truco habitual para que un panel no se recorte),
     * al estrechar la ventana el GridBagLayout deja de poder usar tamanos
     * preferidos y pasa a los minimos; el minimo de un BoxLayout vertical con
     * areas de texto envolventes es enorme y absurdo (miles de pixeles), asi
     * que PanelJugar se desmontaba: aparecian huecos gigantes y el boton JUGAR
     * quedaba fuera de la pantalla. Verificado con una captura antes y despues.
     */
    private static final class PanelCentrador extends JPanel implements Scrollable {
        PanelCentrador() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientacion, int direccion) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientacion, int direccion) { return r.height; }

        @Override public boolean getScrollableTracksViewportWidth() {
            Container padre = getParent();
            return padre == null || padre.getWidth() >= getPreferredSize().width;
        }

        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ----------------------------------------------------------- Navegacion

    private void mostrarSeccion(Seccion seccion) {
        // En modo simple no hay mas seccion que Jugar, salvo el desvio a
        // Ajustes que ofrece PanelJugar cuando la instalacion esta rota.
        if (!config.advancedMode && seccion != Seccion.JUGAR && seccion != Seccion.AJUSTES) {
            seccion = Seccion.JUGAR;
        }
        seccionActual = seccion;
        tarjetas.show(contenido, seccion.tarjeta);
        barraLateral.setSeleccion(seccion);
        barraVolver.setVisible(!config.advancedMode && seccion != Seccion.JUGAR);

        // Enter equivale a JUGAR, pero solo con la pantalla de jugar delante:
        // en Ajustes o en el buscador de Mods seria una sorpresa desagradable.
        getRootPane().setDefaultButton(seccion == Seccion.JUGAR ? botonJugar : null);

        // Cada seccion relee lo suyo al entrar; el disco pudo cambiar.
        switch (seccion) {
            case JUGAR:     panelJugar.refrescar();     break;
            case MODS:      panelMods.refrescar();      break;
            case SKINS:     panelSkins.refrescar();     break;
            case AJUSTES:   panelAjustes.refrescar();   break;
            case REGISTROS: panelRegistros.refrescar(); break;
        }

        if (!config.advancedMode) ajustarTamano(seccion != Seccion.JUGAR);
        if (seccion == Seccion.JUGAR) {
            SwingUtilities.invokeLater(() -> {
                if (campoNombre != null) campoNombre.requestFocusInWindow();
                SwingUtilities.invokeLater(this::scrollJugarArriba);
            });
        }
        contenido.revalidate();
        contenido.repaint();
    }

    /**
     * Reconfigura la ventana segun el modo guardado en la configuracion.
     * {@code enCaliente} distingue el arranque del cambio de modo con la
     * ventana ya abierta (entonces se conserva su posicion en pantalla).
     */
    private void aplicarModo(boolean enCaliente) {
        boolean avanzado = config.advancedMode;
        barraLateral.setVisible(avanzado);
        etiquetaJugador.setText(textoJugador());

        if (!avanzado && seccionActual != Seccion.JUGAR && seccionActual != Seccion.AJUSTES) {
            mostrarSeccion(Seccion.JUGAR);
            return;
        }
        barraVolver.setVisible(!avanzado && seccionActual != Seccion.JUGAR);

        ajustarTamano(avanzado || seccionActual != Seccion.JUGAR);
        revalidate();
        repaint();
    }

    /** Tamano y minimo de la ventana, sin salirse nunca de la pantalla. */
    private void ajustarTamano(boolean grande) {
        Dimension deseado = grande ? TAMANO_AVANZADO : tamanoSimpleDeseado();
        Rectangle util = pantallaUtil();
        int ancho = Math.min(deseado.width, util.width);
        int alto = Math.min(deseado.height, util.height);

        Dimension minimo = grande ? MINIMO_AVANZADO : MINIMO_SIMPLE;
        setMinimumSize(new Dimension(Math.min(minimo.width, ancho), Math.min(minimo.height, alto)));

        if (getWidth() == ancho && getHeight() == alto) return;
        setSize(ancho, alto);

        // Al crecer (por ejemplo al ir a Ajustes desde el modo simple) la
        // ventana podia salirse por la derecha o por abajo: se devuelve dentro
        // de la pantalla en vez de dejar media interfaz fuera.
        if (isShowing()) {
            int x = Math.max(util.x, Math.min(getX(), util.x + util.width - ancho));
            int y = Math.max(util.y, Math.min(getY(), util.y + util.height - alto));
            if (x != getX() || y != getY()) setLocation(x, y);
        }
    }

    /**
     * Alto real del modo simple, calculado sobre el contenido de PanelJugar
     * en vez de un numero fijo pensado para el peor caso (instalacion rota).
     * En el caso normal -- instalacion sana, sin bloque de problemas -- un
     * alto fijo pensado para el caso roto dejaba un tercio de la ventana
     * vacio. Se recalcula cada vez que se entra en la seccion Jugar (el
     * panel ya ha decidido para entonces si el bloque de problemas se
     * muestra o no), asi que crece y encoge solo cuando hace falta.
     */
    private Dimension tamanoSimpleDeseado() {
        int altoContenido = panelJugar.getPreferredSize().height;
        // Relleno de la tarjeta (arriba+abajo) y del centrador que la envuelve
        // (ver construirTarjetaJugar): sin esto el calculo queda corto y el
        // contenido asoma pegado al borde de la ventana.
        int margen = (Tema.ESPACIO_PEQUENO + Tema.ESPACIO_GRANDE) + (Tema.ESPACIO_MEDIO * 2);
        int alto = altoContenido + margen;

        // Cromo de la ventana (barra de titulo + bordes del marco): solo se
        // conoce con precision una vez que el marco nativo existe. Antes de
        // eso (primera construccion) se usa una estimacion razonable; se
        // autocorrige en la siguiente llamada, ya con la ventana visible.
        Insets marco = getInsets();
        int cromo = (marco.top + marco.bottom > 0) ? (marco.top + marco.bottom) : 40;
        alto += cromo;

        alto = Math.max(alto, MINIMO_SIMPLE.height);
        return new Dimension(ANCHO_SIMPLE, alto);
    }

    private Rectangle pantallaUtil() {
        try {
            Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (r.width > 200 && r.height > 200) return r;
        } catch (Throwable t) {
            // sin entorno grafico util seguimos con un tamano razonable
        }
        return new Rectangle(0, 0, 1280, 900);
    }

    // ------------------------------------------------------------ Callbacks

    /** PanelJugar acaba de guardar el nombre del jugador. */
    private void alGuardarDesdeJugar() {
        etiquetaJugador.setText(textoJugador());
    }

    /**
     * Consola del juego -> PanelRegistros. PanelJugar no avisa por otro canal
     * de que empieza una partida, asi que su primera linea sirve de senal para
     * dejar la consola limpia, igual que hacia la ventana anterior.
     */
    private void alLineaDeConsola(String linea) {
        String texto = (linea != null) ? linea : "";
        if (texto.startsWith("== Lanzando ")) {
            panelRegistros.limpiarConsola();
        }
        panelRegistros.anexarLinea(texto);
    }

    /** Se activo o desactivo un mod: cambia la cuenta que muestra PanelJugar. */
    private void alCambiarMods() {
        panelJugar.refrescar();
    }

    /** Se guardaron ajustes: rutas, memoria y modo pueden haber cambiado. */
    private void alGuardarConfig() {
        panelJugar.refrescar();
        panelSkins.refrescar();
        etiquetaJugador.setText(textoJugador());
        aplicarModo(true);
    }

    /** El interruptor de modo avanzado: la ventana se reconfigura en caliente. */
    private void alCambiarModo() {
        aplicarModo(true);
        if (!config.advancedMode && seccionActual != Seccion.AJUSTES) {
            mostrarSeccion(Seccion.JUGAR);
        } else {
            barraLateral.setSeleccion(seccionActual);
            barraVolver.setVisible(!config.advancedMode && seccionActual != Seccion.JUGAR);
        }
    }

    private String textoJugador() {
        return (config.playerName == null || config.playerName.isBlank())
                ? "sin nombre" : config.playerName;
    }

    // --------------------------------------------------------------- Cierre

    /** Cierre de la ventana: no dejar el juego huerfano sin avisar. */
    private void alCerrar() {
        if (panelJugar.juegoEnEjecucion()) {
            // Con opciones propias, no YES_NO_OPTION: los botones de Swing
            // salen en el idioma del sistema y aqui dicen lo que hacen.
            Object[] opciones = {"Seguir aquí", "Cerrar el launcher"};
            int r = JOptionPane.showOptionDialog(this,
                    "El juego sigue abierto.\n\n"
                            + "Si cierras el launcher, el juego seguira funcionando\n"
                            + "y perderas la consola de esta partida.",
                    "El juego sigue abierto", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (r != 1) return;
        }
        dispose();
        System.exit(0);
    }

    // ---------------------------------------------------------------- Utiles

    /** Primer descendiente de la clase pedida; null si no hay ninguno. */
    private static <T extends Component> T buscar(Container raiz, Class<T> clase) {
        for (Component c : raiz.getComponents()) {
            if (clase.isInstance(c)) return clase.cast(c);
            if (c instanceof Container) {
                T encontrado = buscar((Container) c, clase);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    /** Icono de la ventana: el emblema de las dos almas, dibujado con Java2D. */
    private static BufferedImage iconoVentana(int lado) {
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Tema.antialias(g2);
        g2.setColor(Tema.FONDO_ALTO);
        g2.fill(new RoundRectangle2D.Float(0, 0, lado, lado, lado * 0.28f, lado * 0.28f));

        float d = lado * 0.46f;
        Area alma1 = new Area(new Ellipse2D.Float(lado * 0.14f, lado * 0.24f, d, d));
        Area alma2 = new Area(new Ellipse2D.Float(lado * 0.40f, lado * 0.30f, d, d));
        Area comun = new Area(alma1);
        comun.intersect(alma2);
        g2.setColor(new Color(Tema.ACENTO.getRed(), Tema.ACENTO.getGreen(), Tema.ACENTO.getBlue(), 90));
        g2.fill(comun);
        g2.setStroke(new BasicStroke(Math.max(2f, lado * 0.045f)));
        g2.setColor(Tema.ACENTO_FUERTE);
        g2.draw(alma1);
        g2.setColor(Tema.ACENTO);
        g2.draw(alma2);
        g2.dispose();
        return img;
    }

    // --------------------------------------------------------- Barra lateral

    /**
     * Barra lateral fija del modo avanzado. La pinta la ventana, no los
     * paneles: es el unico sitio donde vive la navegacion.
     */
    private final class BarraLateral extends JPanel {

        private final List<BotonNav> botones = new ArrayList<>();

        BarraLateral() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setPreferredSize(new Dimension(ANCHO_LATERAL, 0));
            setMinimumSize(new Dimension(ANCHO_LATERAL, 0));
            setMaximumSize(new Dimension(ANCHO_LATERAL, Integer.MAX_VALUE));
            setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_MEDIO,
                    Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO));

            add(etiquetaLateral("DOS ALMAS", Tema.subtitulo(), Tema.ACENTO));
            add(etiquetaLateral("Launcher del modpack", Tema.etiqueta(), Tema.TEXTO_DEBIL));
            add(Box.createVerticalStrut(Tema.ESPACIO_GRANDE));

            for (Seccion s : Seccion.values()) {
                BotonNav b = new BotonNav(s);
                botones.add(b);
                add(b);
                add(Box.createVerticalStrut(Tema.ESPACIO_XS));
            }

            add(Box.createVerticalGlue());

            Tema.Separador separador = new Tema.Separador();
            separador.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(separador);
            add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

            add(etiquetaLateral("JUGADOR", Tema.etiqueta(), Tema.TEXTO_DEBIL));

            etiquetaJugador.setFont(Tema.cuerpoNegrita());
            etiquetaJugador.setForeground(Tema.TEXTO_SUAVE);
            etiquetaJugador.setAlignmentX(Component.LEFT_ALIGNMENT);
            etiquetaJugador.setBorder(new EmptyBorder(0, Tema.ESPACIO_PEQUENO, 0, 0));
            add(etiquetaJugador);

            add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
            add(etiquetaLateral("Minecraft 1.20.1 -- Forge", Tema.etiqueta(), Tema.TEXTO_DEBIL));
        }

        private JLabel etiquetaLateral(String texto, java.awt.Font fuente, Color color) {
            JLabel l = new JLabel(texto);
            l.setFont(fuente);
            l.setForeground(color);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(new EmptyBorder(0, Tema.ESPACIO_PEQUENO, 0, 0));
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
            return l;
        }

        void setSeleccion(Seccion seccion) {
            for (BotonNav b : botones) b.setSeleccionado(b.seccion == seccion);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Tema.SUPERFICIE);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Tema.BORDE_SUAVE);
            g2.drawLine(w - 1, 0, w - 1, h);
            g2.dispose();
        }
    }

    /** Boton de navegacion: sin bordes de Swing, todo pintado con Java2D. */
    private final class BotonNav extends JComponent {

        private static final int ALTO = 40;

        private final Seccion seccion;
        private boolean seleccionado;
        private boolean sobre;

        BotonNav(Seccion seccion) {
            this.seccion = seccion;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Ir a " + seccion.etiqueta);
            setPreferredSize(new Dimension(ANCHO_LATERAL, ALTO));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTO));
            setMinimumSize(new Dimension(80, ALTO));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { sobre = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { mostrarSeccion(BotonNav.this.seccion); }
            });
        }

        void setSeleccionado(boolean valor) {
            if (seleccionado != valor) {
                seleccionado = valor;
                repaint();
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth();
            int h = getHeight();

            if (seleccionado) {
                g2.setColor(Tema.SUPERFICIE_ELEVADA);
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
                g2.setColor(Tema.ACENTO);
                g2.fill(new RoundRectangle2D.Float(0, h * 0.18f, 3f, h * 0.64f, 3f, 3f));
            } else if (sobre) {
                g2.setColor(new Color(Tema.SUPERFICIE_ELEVADA.getRed(), Tema.SUPERFICIE_ELEVADA.getGreen(),
                        Tema.SUPERFICIE_ELEVADA.getBlue(), 130));
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
            }

            Color tinta = seleccionado ? Tema.ACENTO_FUERTE : (sobre ? Tema.TEXTO : Tema.TEXTO_SUAVE);
            pintarIcono(g2, seccion, 14, (h - 16) / 2, 16, tinta);

            g2.setColor(tinta);
            g2.setFont(seleccionado ? Tema.cuerpoNegrita() : Tema.cuerpo());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(seccion.etiqueta, 42, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Iconos de la navegacion dibujados a mano: ni imagenes ni fuentes raras. */
    private static void pintarIcono(Graphics2D g, Seccion seccion, int x, int y, int lado, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        Tema.antialias(g2);
        g2.translate(x, y);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float l = lado;

        switch (seccion) {
            case JUGAR: {
                GeneralPath p = new GeneralPath();
                p.moveTo(l * 0.24f, l * 0.12f);
                p.lineTo(l * 0.88f, l * 0.50f);
                p.lineTo(l * 0.24f, l * 0.88f);
                p.closePath();
                g2.fill(p);
                break;
            }
            case MODS: {
                // Tres capas apiladas: la metafora de "los jars del pack".
                for (int i = 0; i < 3; i++) {
                    float yy = l * (0.14f + i * 0.26f);
                    g2.draw(new RoundRectangle2D.Float(l * 0.10f, yy, l * 0.80f, l * 0.17f, 3f, 3f));
                }
                break;
            }
            case SKINS: {
                g2.draw(new Ellipse2D.Float(l * 0.31f, l * 0.08f, l * 0.38f, l * 0.38f));
                GeneralPath p = new GeneralPath();
                p.moveTo(l * 0.12f, l * 0.92f);
                p.quadTo(l * 0.50f, l * 0.48f, l * 0.88f, l * 0.92f);
                g2.draw(p);
                break;
            }
            case AJUSTES: {
                g2.draw(new Ellipse2D.Float(l * 0.31f, l * 0.31f, l * 0.38f, l * 0.38f));
                for (int i = 0; i < 6; i++) {
                    double ang = Math.PI * i / 3.0;
                    float cx = l * 0.50f;
                    float cy = l * 0.50f;
                    g2.drawLine((int) (cx + Math.cos(ang) * l * 0.28f), (int) (cy + Math.sin(ang) * l * 0.28f),
                            (int) (cx + Math.cos(ang) * l * 0.46f), (int) (cy + Math.sin(ang) * l * 0.46f));
                }
                break;
            }
            case REGISTROS: {
                float[] anchos = {0.86f, 0.62f, 0.78f, 0.48f};
                for (int i = 0; i < anchos.length; i++) {
                    float yy = l * (0.18f + i * 0.22f);
                    g2.drawLine((int) (l * 0.10f), (int) yy, (int) (l * (0.10f + anchos[i] * 0.86f)), (int) yy);
                }
                break;
            }
        }
        g2.dispose();
    }
}
