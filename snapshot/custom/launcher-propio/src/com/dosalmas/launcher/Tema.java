package com.dosalmas.launcher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Sistema de diseno de RPG Dos Almas: paleta, tipografia, espaciado y un
 * puñado de componentes propios pintados con Java2D. Esta clase no depende
 * de ningun panel: solo aporta piezas para que los paneles se construyan
 * encima.
 *
 * Direccion visual: fondo casi negro calido con un dorado como unico acento
 * (marca ya establecida del modpack). Nada de colores "gamer" saturados: el
 * dorado se reserva para la accion principal y los estados; el resto de la
 * interfaz vive en grises calidos para que ese dorado destaque de verdad.
 */
public final class Tema {

    private Tema() {}

    // ------------------------------------------------------------- Paleta

    /** Fondo de ventana, la capa mas profunda. */
    public static final Color FONDO             = new Color(22, 19, 16);
    /** Segundo tono para el degradado de fondo (un pelin mas claro). */
    public static final Color FONDO_ALTO         = new Color(30, 26, 22);
    /** Superficie normal: tarjetas, paneles de contenido. */
    public static final Color SUPERFICIE         = new Color(38, 34, 30);
    /** Superficie elevada: hover, fila activa, campos de texto. */
    public static final Color SUPERFICIE_ELEVADA = new Color(50, 44, 38);
    /** Borde sutil entre superficies. */
    public static final Color BORDE              = new Color(66, 58, 48);
    /** Borde todavia mas discreto (separadores, filas de lista). */
    public static final Color BORDE_SUAVE        = new Color(52, 46, 40);
    /** Texto principal, alto contraste sobre FONDO/SUPERFICIE. */
    public static final Color TEXTO              = new Color(234, 226, 212);
    /** Texto secundario: ayudas, descripciones, estado no urgente. */
    public static final Color TEXTO_SUAVE         = new Color(180, 168, 150);
    /** Texto terciario: marcas de agua, texto deshabilitado. */
    public static final Color TEXTO_DEBIL         = new Color(122, 112, 98);
    /** Acento de marca: dorado. Boton principal, foco, seleccion. */
    public static final Color ACENTO             = new Color(198, 155, 71);
    /** Acento en hover: un poco mas claro que ACENTO. */
    public static final Color ACENTO_FUERTE       = new Color(218, 180, 104);
    /** Acento apagado: para fondos de seleccion, franjas, chips suaves. */
    public static final Color ACENTO_APAGADO      = new Color(88, 72, 42);
    /** Color de texto que va SOBRE un fondo ACENTO (debe ser oscuro). */
    public static final Color ACENTO_TEXTO        = new Color(30, 23, 14);
    public static final Color EXITO              = new Color(126, 178, 110);
    public static final Color AVISO              = new Color(216, 170, 84);
    public static final Color PELIGRO            = new Color(198, 94, 86);

    /** Estado semantico generico, usado por Insignia y por los paneles. */
    public enum Estado { EXITO, AVISO, PELIGRO, NEUTRO }

    /** Color de acento correspondiente a un estado (para puntos, chips, texto). */
    public static Color colorDeEstado(Estado estado) {
        switch (estado) {
            case EXITO:   return EXITO;
            case AVISO:   return AVISO;
            case PELIGRO: return PELIGRO;
            default:      return TEXTO_SUAVE;
        }
    }

    // --------------------------------------------------------- Tipografia
    //
    // Solo fuentes logicas: en Windows y Linux resuelven a una fuente distinta
    // pero equivalente (Segoe/Arial vs DejaVu), asi que el layout no cambia
    // de una maquina a otra.

    public static Font titulo()        { return new Font(Font.SANS_SERIF, Font.BOLD, 26); }
    public static Font subtitulo()     { return new Font(Font.SANS_SERIF, Font.BOLD, 16); }
    public static Font cuerpo()        { return new Font(Font.SANS_SERIF, Font.PLAIN, 14); }
    public static Font cuerpoNegrita() { return new Font(Font.SANS_SERIF, Font.BOLD, 14); }
    public static Font etiqueta()      { return new Font(Font.SANS_SERIF, Font.PLAIN, 12); }
    public static Font mono()          { return new Font(Font.MONOSPACED, Font.PLAIN, 12); }

    // ----------------------------------------------------------- Espaciado

    public static final int ESPACIO_XS     = 4;
    public static final int ESPACIO_PEQUENO = 8;
    public static final int ESPACIO_MEDIO   = 14;
    public static final int ESPACIO_GRANDE  = 22;
    public static final int ESPACIO_XL      = 34;

    // -------------------------------------------------------------- Radios

    public static final int RADIO_PEQUENO = 8;
    public static final int RADIO_MEDIO   = 14;
    public static final int RADIO_GRANDE  = 20;

    // ------------------------------------------------------------- Utiles

    /** Activa antialiasing de trazo y texto sobre un Graphics2D. */
    public static void antialias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static Color conAlfa(Color c, int alfa) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alfa);
    }

    // ============================================================ TEMA.aplicar

    /**
     * Ajusta los defaults de UIManager para que cualquier componente estandar
     * de Swing (JOptionPane, JScrollBar, JToolTip, listas, combos...) herede
     * el aspecto, aunque no sea uno de los componentes propios de este
     * archivo. Debe llamarse una vez, antes de crear la ventana.
     */
    public static void aplicar() {
        try {
            // Metal (cross-platform) en vez del Look&Feel nativo: asi Windows
            // y Linux parten del mismo punto y nuestros overrides no chocan
            // con una decoracion nativa que no controlamos.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // seguimos con lo que haya; no es motivo para no abrir la ventana
        }

        Font cuerpo = cuerpo();
        Font etiqueta = etiqueta();
        String[] clavesFuenteCuerpo = {
                "Label.font", "Button.font", "CheckBox.font", "RadioButton.font",
                "TextField.font", "TextArea.font", "PasswordField.font",
                "ComboBox.font", "List.font", "Table.font", "TableHeader.font",
                "TabbedPane.font", "Menu.font", "MenuItem.font", "MenuBar.font",
                "OptionPane.messageFont", "OptionPane.buttonFont", "Panel.font",
                "ScrollPane.font", "Viewport.font", "TitledBorder.font",
        };
        for (String clave : clavesFuenteCuerpo) UIManager.put(clave, cuerpo);
        UIManager.put("ToolTip.font", etiqueta);

        // ----------------------------------------------------- superficies
        UIManager.put("control", SUPERFICIE);
        UIManager.put("info", SUPERFICIE_ELEVADA);
        UIManager.put("nimbusLightBackground", FONDO);
        UIManager.put("Panel.background", FONDO);
        UIManager.put("OptionPane.background", FONDO);
        UIManager.put("OptionPane.messageForeground", TEXTO);
        UIManager.put("ScrollPane.background", FONDO);
        UIManager.put("Viewport.background", FONDO);

        // Botones de los cuadros de dialogo en español. Sin esto, Swing los
        // saca en el idioma del sistema y en un equipo con locale en ingles
        // aparecian "Yes"/"No"/"OK" en mitad de una interfaz en español.
        UIManager.put("OptionPane.yesButtonText", "Sí");
        UIManager.put("OptionPane.noButtonText", "No");
        UIManager.put("OptionPane.okButtonText", "Aceptar");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
        UIManager.put("FileChooser.openButtonText", "Abrir");
        UIManager.put("FileChooser.saveButtonText", "Guardar");
        UIManager.put("FileChooser.cancelButtonText", "Cancelar");

        // ---------------------------------------------------------- texto
        UIManager.put("text", TEXTO);
        UIManager.put("textText", TEXTO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("OptionPane.messageForeground", TEXTO);

        // ------------------------------------------------------- tooltips
        UIManager.put("ToolTip.background", SUPERFICIE_ELEVADA);
        UIManager.put("ToolTip.foreground", TEXTO);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        // --------------------------------------------------------- campos
        UIManager.put("TextField.background", SUPERFICIE_ELEVADA);
        UIManager.put("TextField.foreground", TEXTO);
        UIManager.put("TextField.caretForeground", ACENTO);
        UIManager.put("TextField.selectionBackground", ACENTO_APAGADO);
        UIManager.put("TextField.selectionForeground", TEXTO);
        UIManager.put("TextArea.background", SUPERFICIE);
        UIManager.put("TextArea.foreground", TEXTO);
        UIManager.put("TextArea.caretForeground", ACENTO);
        UIManager.put("TextArea.selectionBackground", ACENTO_APAGADO);
        UIManager.put("TextArea.selectionForeground", TEXTO);

        // ------------------------------------------------ listas y tablas
        UIManager.put("List.background", SUPERFICIE);
        UIManager.put("List.foreground", TEXTO);
        UIManager.put("List.selectionBackground", ACENTO_APAGADO);
        UIManager.put("List.selectionForeground", TEXTO);
        UIManager.put("Table.background", SUPERFICIE);
        UIManager.put("Table.foreground", TEXTO);
        UIManager.put("Table.selectionBackground", ACENTO_APAGADO);
        UIManager.put("Table.selectionForeground", TEXTO);
        UIManager.put("Table.gridColor", BORDE_SUAVE);
        UIManager.put("ComboBox.background", SUPERFICIE_ELEVADA);
        UIManager.put("ComboBox.foreground", TEXTO);
        UIManager.put("ComboBox.selectionBackground", ACENTO_APAGADO);
        UIManager.put("ComboBox.selectionForeground", TEXTO);

        // -------------------------------------------------------- menus
        UIManager.put("Menu.background", SUPERFICIE);
        UIManager.put("Menu.foreground", TEXTO);
        UIManager.put("MenuItem.background", SUPERFICIE);
        UIManager.put("MenuItem.foreground", TEXTO);
        UIManager.put("MenuItem.selectionBackground", ACENTO_APAGADO);
        UIManager.put("MenuItem.selectionForeground", TEXTO);

        // ---------------------------------------------------------- splitpane
        // Sin esto, BasicSplitPaneUI.installDefaults() pone un highlight
        // BLANCO PURO de Metal en el borde derecho e inferior de cualquier
        // JSplitPane (el unico cromo de Swing crudo que se colaba en toda la
        // interfaz). Anularlo aqui es la defensa de fondo; ademas cada
        // JSplitPane propio debe llamar setBorder(null) DESPUES de setUI(...).
        UIManager.put("SplitPane.border", null);
        UIManager.put("SplitPaneDivider.border", null);

        // --------------------------------------------------- barra deslizante
        UIManager.put("ScrollBarUI", TemaScrollBarUI.class.getName());
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.background", FONDO);
        UIManager.put("ScrollBar.track", FONDO);
        UIManager.put("ScrollBar.thumb", BORDE);

        // ------------------------------------------- sin anillo de foco feo
        Color transparente = new Color(0, 0, 0, 0);
        String[] clavesFoco = {
                "Button.focus", "CheckBox.focus", "ComboBox.focus", "RadioButton.focus",
                "ScrollBar.focus", "Slider.focus", "TabbedPane.focus", "ToggleButton.focus",
                "TextField.focus", "List.focusCellHighlightBorder",
        };
        for (String clave : clavesFoco) UIManager.put(clave, transparente);
    }

    /** Barra de desplazamiento fina y plana, sin flechas ni bisel de Metal. */
    public static final class TemaScrollBarUI extends BasicScrollBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return new TemaScrollBarUI();
        }

        @Override protected JButton createDecreaseButton(int orientation) { return botonInvisible(); }
        @Override protected JButton createIncreaseButton(int orientation) { return botonInvisible(); }

        private static JButton botonInvisible() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(FONDO);
            g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g2.dispose();
        }

        @Override protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle bounds) {
            if (bounds.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            Color color = isThumbRollover() ? ACENTO : BORDE;
            int margen = 3;
            g2.setColor(color);
            g2.fillRoundRect(bounds.x + margen, bounds.y + margen,
                    bounds.width - margen * 2, bounds.height - margen * 2, 8, 8);
            g2.dispose();
        }
    }

    // ======================================================== Componentes

    /**
     * Fondo de ventana: degradado calido de arriba a abajo, una vineta sutil
     * y un grano fino generado por codigo (nada de imagenes externas). Usalo
     * como panel de contenido de la ventana, con un LayoutManager que
     * necesites (por defecto BorderLayout).
     */
    public static class PanelFondo extends JPanel {
        private BufferedImage granoCache;
        private int granoAnchoCache = -1;
        private int granoAltoCache = -1;

        public PanelFondo() { this(new java.awt.BorderLayout()); }

        public PanelFondo(LayoutManager gestor) {
            super(gestor);
            setOpaque(true);
        }

        @Override protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);

            g2.setPaint(new GradientPaint(0, 0, FONDO_ALTO, 0, h, FONDO));
            g2.fillRect(0, 0, w, h);

            float radio = Math.max(w, h) * 0.8f;
            RadialGradientPaint vineta = new RadialGradientPaint(
                    new Point2D.Float(w / 2f, h * 0.35f), radio,
                    new float[] {0f, 1f},
                    new Color[] {conAlfa(Color.BLACK, 0), conAlfa(Color.BLACK, 100)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g2.setPaint(vineta);
            g2.fillRect(0, 0, w, h);

            g2.drawImage(granoDe(w, h), 0, 0, null);
            g2.dispose();
        }

        /** Textura de grano cacheada por tamano: no se recalcula en cada repintado. */
        private BufferedImage granoDe(int w, int h) {
            if (granoCache != null && granoAnchoCache == w && granoAltoCache == h) return granoCache;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            Random r = new Random(20260724L); // semilla fija: mismo grano siempre, no parpadea
            g2.setColor(conAlfa(Color.WHITE, 9));
            int puntos = Math.min(6000, (w * h) / 700);
            for (int i = 0; i < puntos; i++) {
                g2.fillRect(r.nextInt(w), r.nextInt(h), 1, 1);
            }
            g2.dispose();
            granoCache = img;
            granoAnchoCache = w;
            granoAltoCache = h;
            return img;
        }
    }

    /** Base comun de los dos botones: sin relleno de Swing, estado propio. */
    private abstract static class BotonBase extends JButton {
        boolean sobre = false;
        boolean pulsado = false;

        BotonBase(String texto) {
            super(texto);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(cuerpoNegrita());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(ESPACIO_PEQUENO, ESPACIO_GRANDE, ESPACIO_PEQUENO, ESPACIO_GRANDE));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { sobre = false; pulsado = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { pulsado = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { pulsado = false; repaint(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 96), Math.max(d.height, 38));
        }
    }

    /** Boton de accion principal: relleno dorado, para UNA accion por pantalla. */
    public static final class BotonPrimario extends BotonBase {
        public BotonPrimario(String texto) {
            super(texto);
            setForeground(ACENTO_TEXTO);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            Color relleno;
            if (!isEnabled())      relleno = conAlfa(ACENTO, 90);
            else if (pulsado)      relleno = ACENTO.darker();
            else if (sobre)        relleno = ACENTO_FUERTE;
            else                   relleno = ACENTO;
            g2.setColor(relleno);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIO_MEDIO, RADIO_MEDIO);
            g2.dispose();
            setForeground(isEnabled() ? ACENTO_TEXTO : conAlfa(ACENTO_TEXTO, 160));
            super.paintComponent(g);
        }
    }

    /** Boton secundario: contorno sutil, para acciones que acompañan a la principal. */
    public static final class BotonSecundario extends BotonBase {
        public BotonSecundario(String texto) {
            super(texto);
            setForeground(TEXTO);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            if (pulsado || sobre) {
                g2.setColor(sobre && !pulsado ? SUPERFICIE_ELEVADA : BORDE_SUAVE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIO_MEDIO, RADIO_MEDIO);
            }
            g2.setColor(isEnabled() ? BORDE : BORDE_SUAVE);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIO_MEDIO, RADIO_MEDIO);
            g2.dispose();
            setForeground(isEnabled() ? TEXTO : TEXTO_DEBIL);
            super.paintComponent(g);
        }
    }

    /** Panel de superficie con esquinas redondeadas y borde sutil. */
    public static class Tarjeta extends JPanel {
        public Tarjeta() { this(new java.awt.BorderLayout(ESPACIO_MEDIO, ESPACIO_MEDIO)); }

        public Tarjeta(LayoutManager gestor) {
            super(gestor);
            setOpaque(false);
            setBorder(new EmptyBorder(ESPACIO_GRANDE, ESPACIO_GRANDE, ESPACIO_GRANDE, ESPACIO_GRANDE));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(SUPERFICIE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, RADIO_GRANDE, RADIO_GRANDE));
            g2.setColor(BORDE);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, RADIO_GRANDE, RADIO_GRANDE));
            g2.dispose();
        }
    }

    /** Campo de texto con borde redondeado, texto de sugerencia y estado de error. */
    public static final class CampoTexto extends JTextField {
        private String sugerencia = "";
        private boolean error = false;

        public CampoTexto() { this(0); }

        public CampoTexto(int columnas) {
            super(columnas);
            setOpaque(false);
            setForeground(TEXTO);
            setCaretColor(ACENTO);
            setSelectionColor(ACENTO_APAGADO);
            setSelectedTextColor(TEXTO);
            setFont(cuerpo());
            setBorder(new EmptyBorder(ESPACIO_PEQUENO, ESPACIO_MEDIO, ESPACIO_PEQUENO, ESPACIO_MEDIO));
        }

        public void setSugerencia(String texto) { this.sugerencia = (texto != null) ? texto : ""; repaint(); }
        public String getSugerencia() { return sugerencia; }

        public void setError(boolean error) { this.error = error; repaint(); }
        public boolean isError() { return error; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(SUPERFICIE_ELEVADA);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, RADIO_PEQUENO, RADIO_PEQUENO));
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && !sugerencia.isEmpty()) {
                Graphics2D gp = (Graphics2D) g.create();
                antialias(gp);
                gp.setColor(TEXTO_DEBIL);
                gp.setFont(getFont());
                FontMetrics fm = gp.getFontMetrics();
                java.awt.Insets in = getInsets();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                gp.drawString(sugerencia, in.left, y);
                gp.dispose();
            }

            Graphics2D gb = (Graphics2D) g.create();
            antialias(gb);
            gb.setColor(error ? PELIGRO : (hasFocus() ? ACENTO : BORDE));
            gb.setStroke(new BasicStroke(hasFocus() || error ? 1.6f : 1f));
            gb.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, RADIO_PEQUENO, RADIO_PEQUENO));
            gb.dispose();
        }
    }

    /** Linea separadora sutil, con etiqueta de seccion opcional. */
    public static final class Separador extends JComponent {
        private final String texto;

        public Separador() { this(null); }

        public Separador(String textoSeccion) {
            this.texto = textoSeccion;
            setOpaque(false);
            setPreferredSize(new Dimension(10, (texto == null) ? 12 : 26));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            int w = getWidth();
            if (texto == null || texto.isEmpty()) {
                int y = getHeight() / 2;
                g2.setColor(BORDE_SUAVE);
                g2.drawLine(0, y, w, y);
            } else {
                g2.setFont(etiqueta());
                g2.setColor(TEXTO_DEBIL);
                FontMetrics fm = g2.getFontMetrics();
                int y = getHeight() / 2;
                int anchoTexto = fm.stringWidth(texto.toUpperCase());
                g2.drawString(texto.toUpperCase(), 0, y + fm.getAscent() / 2 - 1);
                g2.setColor(BORDE_SUAVE);
                int inicioLinea = anchoTexto + ESPACIO_PEQUENO;
                if (inicioLinea < w) g2.drawLine(inicioLinea, y, w, y);
            }
            g2.dispose();
        }
    }

    /** Chip de estado: punto de color + texto, para mostrar salud de la instalacion. */
    public static final class Insignia extends JComponent {
        private String texto;
        private Estado estado;

        public Insignia(String texto, Estado estado) {
            this.texto = texto;
            this.estado = estado;
            setOpaque(false);
            setFont(etiqueta());
        }

        public void setTexto(String texto) { this.texto = texto; revalidate(); repaint(); }
        public void setEstado(Estado estado) { this.estado = estado; repaint(); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(etiqueta());
            int ancho = fm.stringWidth(texto) + 10 /* punto */ + ESPACIO_PEQUENO * 3;
            int alto = fm.getHeight() + ESPACIO_XS * 2;
            return new Dimension(ancho, alto);
        }

        // Una insignia mide lo que mide su texto y nada mas. Sin estos dos
        // limites, un BoxLayout la estiraba a todo el ancho disponible y
        // dejaba de parecer una insignia para parecer una barra.
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(SUPERFICIE_ELEVADA);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, h, h));
            g2.setColor(BORDE_SUAVE);
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, h, h));

            int cx = ESPACIO_PEQUENO + 2;
            int cy = h / 2;
            g2.setColor(colorDeEstado(estado));
            g2.fillOval(cx - 3, cy - 3, 6, 6);

            g2.setFont(etiqueta());
            g2.setColor(TEXTO_SUAVE);
            FontMetrics fm = g2.getFontMetrics();
            int tx = cx + 8;
            int ty = cy + fm.getAscent() / 2 - 1;
            g2.drawString(texto, tx, ty);
            g2.dispose();
        }
    }

    /**
     * Texto de varias lineas que se ajusta al ancho de su contenedor.
     *
     * Un JLabel con <html> calcula mal su tamano dentro de un BoxLayout y
     * recorta la frase a mitad de palabra cuando la ventana se estrecha, sin
     * ningun indicio de que falta texto. Esto reflowa de verdad.
     */
    public static class TextoEnvolvente extends javax.swing.JTextArea {
        public TextoEnvolvente(String texto) {
            super(texto);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
            setFont(etiqueta());
            setForeground(TEXTO_DEBIL);
        }

        @Override public java.awt.Dimension getPreferredSize() {
            java.awt.Container padre = getParent();
            int ancho = (padre != null && padre.getWidth() > 0) ? padre.getWidth() : 260;
            setSize(new java.awt.Dimension(ancho, Short.MAX_VALUE));
            java.awt.Dimension d = super.getPreferredSize();
            return new java.awt.Dimension(ancho, d.height);
        }

        @Override public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

}
