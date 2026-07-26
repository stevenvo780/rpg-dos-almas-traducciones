package com.dosalmas.launcher;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Gestor de mods del pack: lista lo que hay en mods/ y mods-disabled/,
 * permite buscar y filtrar, y activa/desactiva jars moviendolos entre esas
 * dos carpetas (la convencion ya establecida). No anade ni descarga mods
 * nuevos: solo administra los que ya trae el pack.
 *
 * Toda la lectura de disco (134 jars) ocurre en un SwingWorker: nunca en el
 * hilo de la interfaz.
 */
public final class PanelMods extends JPanel {

    private enum Filtro { TODOS, ACTIVOS, DESACTIVADOS }

    private final Config config;
    private final Runnable alCambiarMods;

    private ModsServicio servicio;
    private List<ModsServicio.ModInfo> todosLosMods = new ArrayList<>();
    private Filtro filtroActual = Filtro.TODOS;
    private boolean cargando = false;

    private JLabel contadores;
    private JLabel etiquetaCarpeta;
    private Tema.CampoTexto buscador;
    private JPanel filaFiltros;
    private JButton botonTodos, botonActivos, botonDesactivados;
    private JPanel listaContenedor;
    private JLabel estadoVacio;

    public PanelMods(Config config, Runnable alCambiarMods) {
        super(new BorderLayout(0, Tema.ESPACIO_MEDIO));
        this.config = config;
        this.alCambiarMods = alCambiarMods;
        setOpaque(false);
        setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));
        construirUI();
        refrescar();
    }

    // ============================================================= UI

    private void construirUI() {
        JPanel norte = new JPanel();
        norte.setOpaque(false);
        norte.setLayout(new BoxLayout(norte, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel("Mods");
        titulo.setFont(Tema.titulo());
        titulo.setForeground(Tema.TEXTO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        norte.add(titulo);

        etiquetaCarpeta = new JLabel(" ");
        etiquetaCarpeta.setFont(Tema.mono());
        etiquetaCarpeta.setForeground(Tema.TEXTO_DEBIL);
        etiquetaCarpeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        norte.add(etiquetaCarpeta);

        norte.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
        norte.add(construirAviso());
        norte.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
        norte.add(construirFilaBusquedaYFiltros());
        norte.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        contadores = new JLabel("Cargando mods...");
        contadores.setFont(Tema.etiqueta());
        contadores.setForeground(Tema.TEXTO_SUAVE);
        contadores.setAlignmentX(Component.LEFT_ALIGNMENT);
        norte.add(contadores);

        add(norte, BorderLayout.NORTH);

        listaContenedor = new JPanel();
        listaContenedor.setOpaque(false);
        listaContenedor.setLayout(new BoxLayout(listaContenedor, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listaContenedor,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);

        estadoVacio = new JLabel("Sin resultados.", SwingConstants.CENTER);
        estadoVacio.setFont(Tema.cuerpo());
        estadoVacio.setForeground(Tema.TEXTO_DEBIL);
    }

    /** Aviso permanente: este pack juega en un servidor privado con mods obligatorios. */
    private JComponent construirAviso() {
        BannerAviso banner = new BannerAviso(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Sin alto maximo fijo: este aviso NO puede quedarse a medias. Con 74 px
        // clavados, en una ventana estrecha se cortaba justo antes de decir que
        // desactivar un mod impide entrar al servidor.
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        IconoAdvertencia icono = new IconoAdvertencia();
        banner.add(icono, BorderLayout.WEST);

        Tema.TextoEnvolvente texto = new Tema.TextoEnvolvente(
                "Este pack juega en el servidor privado de RPG Dos Almas. Si desactivas un mod "
                + "que el servidor exige, no podras conectarte hasta que lo vuelvas a activar. "
                + "Cada cambio pide confirmacion.");
        texto.setFont(Tema.cuerpo());
        texto.setForeground(Tema.TEXTO);
        banner.add(texto, BorderLayout.CENTER);
        return banner;
    }

    private JComponent construirFilaBusquedaYFiltros() {
        JPanel fila = new JPanel(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        buscador = new Tema.CampoTexto();
        buscador.setSugerencia("Buscar mod por nombre o archivo...");
        buscador.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { aplicarFiltro(); }
            @Override public void removeUpdate(DocumentEvent e)  { aplicarFiltro(); }
            @Override public void changedUpdate(DocumentEvent e) { aplicarFiltro(); }
        });
        fila.add(buscador, BorderLayout.CENTER);

        filaFiltros = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tema.ESPACIO_XS, 0));
        filaFiltros.setOpaque(false);
        botonTodos        = new JButton();
        botonActivos      = new JButton();
        botonDesactivados = new JButton();
        botonTodos.addActionListener(e        -> { filtroActual = Filtro.TODOS;        aplicarFiltro(); });
        botonActivos.addActionListener(e      -> { filtroActual = Filtro.ACTIVOS;      aplicarFiltro(); });
        botonDesactivados.addActionListener(e -> { filtroActual = Filtro.DESACTIVADOS; aplicarFiltro(); });
        actualizarBotonesFiltro();
        fila.add(filaFiltros, BorderLayout.EAST);

        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.setAlignmentX(Component.LEFT_ALIGNMENT);
        envoltorio.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        envoltorio.add(fila, BorderLayout.CENTER);
        return envoltorio;
    }

    /** Reconstruye los tres botones de filtro: el activo se pinta como primario. */
    private void actualizarBotonesFiltro() {
        filaFiltros.removeAll();
        filaFiltros.add(botonFiltro("Todos", Filtro.TODOS));
        filaFiltros.add(botonFiltro("Activos", Filtro.ACTIVOS));
        filaFiltros.add(botonFiltro("Desactivados", Filtro.DESACTIVADOS));
        filaFiltros.revalidate();
        filaFiltros.repaint();
    }

    private JButton botonFiltro(String texto, Filtro valor) {
        JButton b = valor.equals(filtroActual) ? new Tema.BotonPrimario(texto) : new Tema.BotonSecundario(texto);
        b.addActionListener(e -> { filtroActual = valor; actualizarBotonesFiltro(); aplicarFiltro(); });
        return b;
    }

    // ========================================================= Carga de datos

    /** Recarga la lista de mods desde disco (fuera del hilo de la interfaz). */
    public void refrescar() {
        String gameDirTexto = (config != null) ? config.gameDir : null;
        if (gameDirTexto == null || gameDirTexto.isBlank()) {
            servicio = null;
            todosLosMods = new ArrayList<>();
            etiquetaCarpeta.setForeground(Tema.TEXTO_DEBIL);
            etiquetaCarpeta.setText("No se detecto la carpeta del juego; configurala en Ajustes.");
            contadores.setText("0 activos, 0 desactivados");
            mostrarFilas(new ArrayList<>());
            return;
        }

        Path gameDir;
        try {
            gameDir = Paths.get(gameDirTexto);
        } catch (InvalidPathException e) {
            servicio = null;
            todosLosMods = new ArrayList<>();
            etiquetaCarpeta.setForeground(Tema.PELIGRO);
            etiquetaCarpeta.setText("La ruta del juego en Ajustes no es valida: " + gameDirTexto);
            contadores.setText("0 activos, 0 desactivados");
            mostrarFilas(new ArrayList<>());
            return;
        }

        servicio = new ModsServicio(gameDir);
        etiquetaCarpeta.setForeground(Tema.TEXTO_DEBIL);
        etiquetaCarpeta.setText("Carpeta: " + gameDir.resolve("mods"));
        cargando = true;
        contadores.setText("Cargando mods...");
        listaContenedor.removeAll();
        listaContenedor.add(etiquetaCargando());
        listaContenedor.revalidate();
        listaContenedor.repaint();

        ModsServicio servicioActual = servicio;
        new SwingWorker<ModsServicio.Listado, Void>() {
            @Override protected ModsServicio.Listado doInBackground() {
                return servicioActual.listar();
            }

            @Override protected void done() {
                cargando = false;
                ModsServicio.Listado resultado;
                try {
                    resultado = get();
                } catch (Exception e) {
                    Throwable causa = (e.getCause() != null) ? e.getCause() : e;
                    todosLosMods = new ArrayList<>();
                    etiquetaCarpeta.setForeground(Tema.PELIGRO);
                    etiquetaCarpeta.setText("No se pudo leer la carpeta de mods: " + Mensajes.explicar(causa));
                    actualizarContadores();
                    aplicarFiltro();
                    return;
                }
                todosLosMods = resultado.mods;
                if (!resultado.errores.isEmpty()) {
                    etiquetaCarpeta.setForeground(Tema.PELIGRO);
                    etiquetaCarpeta.setText(resultado.errores.get(0));
                } else {
                    etiquetaCarpeta.setForeground(Tema.TEXTO_DEBIL);
                    etiquetaCarpeta.setText("Carpeta: " + gameDir.resolve("mods"));
                }
                actualizarContadores();
                aplicarFiltro();
            }
        }.execute();
    }

    private JComponent etiquetaCargando() {
        JLabel l = new JLabel("Leyendo " + "mods...", SwingConstants.CENTER);
        l.setFont(Tema.cuerpo());
        l.setForeground(Tema.TEXTO_DEBIL);
        l.setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, 0, 0, 0));
        return l;
    }

    private void actualizarContadores() {
        int activos = 0;
        long total = 0L;
        for (ModsServicio.ModInfo m : todosLosMods) {
            if (m.activo) activos++;
            total += m.tamanoBytes;
        }
        int desactivados = todosLosMods.size() - activos;
        contadores.setText(activos + " activos, " + desactivados + " desactivados, "
                + ModsServicio.formatoTamano(total));
    }

    // ============================================================ Filtrado

    private void aplicarFiltro() {
        String consulta = buscador.getText().trim().toLowerCase(Locale.ROOT);
        List<ModsServicio.ModInfo> filtrados = new ArrayList<>();
        for (ModsServicio.ModInfo m : todosLosMods) {
            if (filtroActual == Filtro.ACTIVOS && !m.activo) continue;
            if (filtroActual == Filtro.DESACTIVADOS && m.activo) continue;
            if (!consulta.isEmpty()
                    && !m.nombre.toLowerCase(Locale.ROOT).contains(consulta)
                    && !m.archivo.toLowerCase(Locale.ROOT).contains(consulta)) continue;
            filtrados.add(m);
        }
        mostrarFilas(filtrados);
    }

    private void mostrarFilas(List<ModsServicio.ModInfo> filas) {
        listaContenedor.removeAll();
        if (cargando) {
            listaContenedor.add(etiquetaCargando());
        } else if (filas.isEmpty()) {
            listaContenedor.add(estadoVacio);
        } else {
            for (ModsServicio.ModInfo m : filas) {
                listaContenedor.add(crearFila(m));
                listaContenedor.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
            }
        }
        listaContenedor.revalidate();
        listaContenedor.repaint();
    }

    // =============================================================== Filas

    private JComponent crearFila(ModsServicio.ModInfo mod) {
        Tema.Tarjeta fila = new Tema.Tarjeta(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
        fila.setBorder(new EmptyBorder(Tema.ESPACIO_PEQUENO + 2, Tema.ESPACIO_MEDIO, Tema.ESPACIO_PEQUENO + 2, Tema.ESPACIO_MEDIO));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        fila.setPreferredSize(new Dimension(10, 58));

        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        JLabel nombre = new JLabel(mod.nombre);
        nombre.setFont(Tema.cuerpoNegrita());
        nombre.setForeground(Tema.TEXTO);
        nombre.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel detalle = new JLabel("v" + mod.version + "  ·  " + mod.tamanoFormateado() + "  ·  " + mod.archivo);
        detalle.setFont(Tema.etiqueta());
        detalle.setForeground(Tema.TEXTO_SUAVE);
        detalle.setAlignmentX(Component.LEFT_ALIGNMENT);
        textos.add(nombre);
        textos.add(detalle);
        fila.add(textos, BorderLayout.CENTER);

        JPanel derecha = new JPanel(new GridBagLayout());
        derecha.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0; gc.gridx = 0; gc.insets = new java.awt.Insets(0, 0, 0, Tema.ESPACIO_MEDIO);
        Tema.Insignia insignia = new Tema.Insignia(
                mod.activo ? "Activo" : "Desactivado",
                mod.activo ? Tema.Estado.EXITO : Tema.Estado.NEUTRO);
        derecha.add(insignia, gc);

        gc.gridx = 1; gc.insets = new java.awt.Insets(0, 0, 0, 0);
        Interruptor interruptor = new Interruptor(mod.activo);
        interruptor.setAlClic(() -> confirmarYAlternar(mod));
        derecha.add(interruptor, gc);

        fila.add(derecha, BorderLayout.EAST);
        return fila;
    }

    // ======================================================= Activar/Desactivar

    private void confirmarYAlternar(ModsServicio.ModInfo mod) {
        boolean activando = !mod.activo;
        String mensaje = activando
                ? "¿Activar <b>" + escaparHtml(mod.nombre) + "</b>?<br><br>"
                        + "Se cargara la proxima vez que se abra el juego. Comprueba que el"
                        + " servidor y el resto del equipo tambien lo tengan activado."
                : "¿Desactivar <b>" + escaparHtml(mod.nombre) + "</b>?<br><br>"
                        + "Este pack juega en el servidor privado de RPG Dos Almas.<br>"
                        + "Si el servidor exige este mod, no podras conectarte hasta"
                        + " que lo vuelvas a activar.";
        boolean confirmado = mostrarDialogoConfirmacion(
                activando ? "Activar mod" : "Desactivar mod",
                mensaje,
                activando ? "Activar" : "Desactivar");
        if (!confirmado) return;
        aplicarCambio(mod, activando);
    }

    private void aplicarCambio(ModsServicio.ModInfo mod, boolean activando) {
        ModsServicio servicioActual = servicio;
        if (servicioActual == null) return;
        new SwingWorker<ModsServicio.Resultado, Void>() {
            @Override protected ModsServicio.Resultado doInBackground() {
                return activando ? servicioActual.activar(mod.archivo) : servicioActual.desactivar(mod.archivo);
            }

            @Override protected void done() {
                ModsServicio.Resultado resultado;
                try {
                    resultado = get();
                } catch (Exception e) {
                    Throwable causa = (e.getCause() != null) ? e.getCause() : e;
                    resultado = ModsServicio.Resultado.error(Mensajes.explicar(causa));
                }
                if (resultado.exito) {
                    if (alCambiarMods != null) alCambiarMods.run();
                    refrescar();
                } else {
                    mostrarDialogoAviso("No se pudo aplicar el cambio", escaparHtml(resultado.mensaje));
                }
            }
        }.execute();
    }

    private static String escaparHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "<br>");
    }

    // ==================================================== Dialogos propios
    //
    // Nada de JOptionPane aqui: sus botones no salen en español si la maquina
    // no tiene el sistema en ese idioma, y su fondo no hereda los colores del
    // tema. Un dialogo propio, con los mismos componentes de Tema que el
    // resto del panel, evita las dos cosas.

    /** Dialogo de confirmacion con Cancelar + una accion principal. Bloquea hasta que se elige. */
    private boolean mostrarDialogoConfirmacion(String titulo, String mensajeHtml, String textoAccion) {
        boolean[] confirmado = {false};
        JDialog dialogo = construirDialogoBase(titulo, mensajeHtml, true, dlg -> {
            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tema.ESPACIO_PEQUENO, 0));
            botones.setOpaque(false);
            Tema.BotonSecundario cancelar = new Tema.BotonSecundario("Cancelar");
            Tema.BotonPrimario aceptar = new Tema.BotonPrimario(textoAccion);
            cancelar.addActionListener(e -> dlg.dispose());
            aceptar.addActionListener(e -> { confirmado[0] = true; dlg.dispose(); });
            dlg.getRootPane().setDefaultButton(aceptar);
            botones.add(cancelar);
            botones.add(aceptar);
            return botones;
        });
        dialogo.setVisible(true); // modal: bloquea aqui hasta dispose()
        return confirmado[0];
    }

    /** Aviso con un unico boton "Aceptar". Bloquea hasta que se cierra. */
    private void mostrarDialogoAviso(String titulo, String mensajeHtml) {
        JDialog dialogo = construirDialogoBase(titulo, mensajeHtml, false, dlg -> {
            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tema.ESPACIO_PEQUENO, 0));
            botones.setOpaque(false);
            Tema.BotonPrimario aceptar = new Tema.BotonPrimario("Aceptar");
            aceptar.addActionListener(e -> dlg.dispose());
            dlg.getRootPane().setDefaultButton(aceptar);
            botones.add(aceptar);
            return botones;
        });
        dialogo.setVisible(true);
    }

    private JDialog construirDialogoBase(String titulo, String mensajeHtml, boolean advertencia,
                                          java.util.function.Function<JDialog, JComponent> filaBotones) {
        Window ventana = SwingUtilities.getWindowAncestor(this);
        JDialog dialogo = (ventana != null)
                ? new JDialog(ventana, titulo, Dialog.ModalityType.APPLICATION_MODAL)
                : new JDialog((java.awt.Frame) null, titulo, true);
        dialogo.setResizable(false);

        JPanel contenido = new JPanel(new BorderLayout(0, Tema.ESPACIO_GRANDE));
        contenido.setOpaque(true);
        contenido.setBackground(Tema.SUPERFICIE);
        contenido.setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));

        JPanel filaMensaje = new JPanel(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
        filaMensaje.setOpaque(false);
        if (advertencia) {
            // BorderLayout.WEST estira el componente a toda la altura de la
            // fila; anclado en un panel con NORTH conserva su tamaño propio
            // (30x30) en vez de un triangulo alargado.
            JPanel envoltorioIcono = new JPanel(new BorderLayout());
            envoltorioIcono.setOpaque(false);
            envoltorioIcono.add(new IconoAdvertencia(), BorderLayout.NORTH);
            filaMensaje.add(envoltorioIcono, BorderLayout.WEST);
        }
        JLabel mensaje = new JLabel("<html><body style='width:320px'>" + mensajeHtml + "</body></html>");
        mensaje.setFont(Tema.cuerpo());
        mensaje.setForeground(Tema.TEXTO);
        filaMensaje.add(mensaje, BorderLayout.CENTER);
        contenido.add(filaMensaje, BorderLayout.CENTER);

        contenido.add(filaBotones.apply(dialogo), BorderLayout.SOUTH);

        dialogo.setContentPane(contenido);
        dialogo.pack();
        dialogo.setLocationRelativeTo(this);
        return dialogo;
    }

    // ============================================================ Piezas propias

    /** Interruptor deslizante activo/inactivo, pintado con Java2D. */
    private static final class Interruptor extends JComponent {
        private boolean encendido;
        private Runnable alClic;

        Interruptor(boolean encendido) {
            this.encendido = encendido;
            setPreferredSize(new Dimension(46, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (alClic != null) alClic.run();
                }
            });
        }

        void setAlClic(Runnable r) { this.alClic = r; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(encendido ? Tema.ACENTO : Tema.BORDE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            int diametro = h - 6;
            int x = encendido ? (w - diametro - 3) : 3;
            g2.setColor(encendido ? Tema.FONDO : Tema.TEXTO_SUAVE);
            g2.fillOval(x, 3, diametro, diametro);
            g2.dispose();
        }
    }

    /** Icono de advertencia dibujado con Java2D: nada de fuentes con emoji. */
    private static final class IconoAdvertencia extends JComponent {
        IconoAdvertencia() {
            setPreferredSize(new Dimension(30, 30));
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth(), h = getHeight();
            int[] xs = {w / 2, 2, w - 2};
            int[] ys = {3, h - 3, h - 3};
            g2.setColor(Tema.AVISO);
            g2.fillPolygon(xs, ys, 3);
            g2.setColor(Tema.FONDO);
            Font f = Tema.cuerpoNegrita();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            String signo = "!";
            int tx = (w - fm.stringWidth(signo)) / 2;
            int ty = h - 6;
            g2.drawString(signo, tx, ty);
            g2.dispose();
        }
    }

    /** Franja de aviso: superficie elevada con un acento lateral de color AVISO. */
    private static final class BannerAviso extends JPanel {
        BannerAviso(LayoutManager gestor) {
            super(gestor);
            setOpaque(false);
            setBorder(new EmptyBorder(Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO, Tema.ESPACIO_MEDIO));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(Tema.SUPERFICIE_ELEVADA);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_MEDIO, Tema.RADIO_MEDIO));
            g2.setColor(Tema.AVISO);
            g2.fill(new RoundRectangle2D.Float(0, 0, 5, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
            g2.setColor(Tema.BORDE_SUAVE);
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_MEDIO, Tema.RADIO_MEDIO));
            g2.dispose();
        }
    }
}
