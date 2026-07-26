package com.dosalmas.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Panel de apariencia del jugador.
 *
 * La restriccion real, dicha con claridad en el propio panel: con cuentas
 * offline Minecraft no descarga skins de Mojang, y este pack no trae ningun
 * mod de skins instalado. Por eso el launcher NO puede poner una skin dentro
 * del juego por si solo. Lo que si hace, y es util de verdad:
 *
 *   - guarda una biblioteca local de PNG validados como skin,
 *   - dibuja una vista previa (frente/espalda) recortando el propio PNG
 *     con Java2D, sin depender de ninguna libreria,
 *   - detecta si algun mod de skins aparece instalado y, si no, explica
 *     exactamente que haria falta para que la skin se viera en el juego,
 *   - muestra el UUID offline estable del nombre actual.
 */
public final class PanelSkins extends JPanel {

    private final Config config;
    private final SkinsServicio servicio = new SkinsServicio();

    private final VistaSkin vista = new VistaSkin();
    private final JPanel listaBiblioteca = new JPanel();
    private ButtonGroup grupoSeleccion = new ButtonGroup();
    private final JCheckBox casillaFina = new JCheckBox("Modelo fino (tipo Alex)");
    private final Tema.BotonSecundario botonFrente = new Tema.BotonSecundario("Frente");
    private final Tema.BotonSecundario botonEspalda = new Tema.BotonSecundario("Espalda");
    private final Tema.BotonPrimario botonActivar = new Tema.BotonPrimario("Usar esta piel");
    private final Tema.BotonSecundario botonEliminar = new Tema.BotonSecundario("Eliminar");
    private final JLabel etiquetaUuid = new JLabel();
    private final JLabel etiquetaSeleccion = new JLabel("Ninguna skin seleccionada");
    private final Tema.Insignia insigniaMod = new Tema.Insignia("Comprobando...", Tema.Estado.NEUTRO);
    private final TextoEnvolvente textoModExplicacion = new TextoEnvolvente("");

    private List<SkinsServicio.SkinInfo> lista = new ArrayList<>();
    private String nombreSeleccionado = null;
    private boolean actualizandoCasilla = false;
    private JScrollPane scrollCentro;

    public PanelSkins(Config config) {
        super(new BorderLayout());
        this.config = config;
        setOpaque(false);
        setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));

        add(construirEncabezado(), BorderLayout.NORTH);

        // PanelAnchoDeViewport, no un JPanel a secas: un panel normal dentro de
        // un JScrollPane con scroll horizontal desactivado no encoge nunca, se
        // queda con su ancho preferido y todo lo que no cabe se recorta en
        // silencio (asi se veian la tarjeta de biblioteca y su texto cortados
        // en el borde). Implementando Scrollable con ancho pegado al viewport,
        // el GridBagLayout (fill=BOTH, weightx=1) si puede encoger la columna
        // de la biblioteca al ancho real disponible.
        JPanel centro = new PanelAnchoDeViewport(new GridBagLayout());
        centro.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new java.awt.Insets(0, 0, 0, Tema.ESPACIO_GRANDE);

        gc.gridx = 0;
        gc.weightx = 0;
        gc.weighty = 1;
        centro.add(construirTarjetaPreview(), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = new java.awt.Insets(0, 0, 0, 0);
        centro.add(construirTarjetaBiblioteca(), gc);

        JScrollPane scroll = new JScrollPane(centro,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        scrollCentro = scroll;
        // El alto envuelto de TextoEnvolvente se recalcula en varias pasadas
        // de layout (depende del ancho real, que solo se conoce cuando el
        // viewport ya tiene tamano); sin esto, la primera pintura a veces
        // arranca con el scroll unos pixeles por debajo del todo y recorta
        // los titulos de las tarjetas. Un solo invokeLater no basta porque
        // esas pasadas de layout siguen encolando trabajo propio: se repite
        // unas cuantas veces hasta que el alto ya no cambia.
        fijarScrollArriba(scroll, 6);
        // Ese apaño solo cubria el instante de la construccion, con el tamano
        // que tuviera la ventana en ese momento. Al entrar de verdad en esta
        // seccion (otro tamano de ventana, otro momento) nadie devolvia el
        // scroll a (0,0): se abria ya desplazado y escondia el titulo y el
        // boton "Importar PNG...". Por eso tambien se dispara desde
        // refrescar() (se llama cada vez que se muestra la seccion) y desde
        // cualquier redimension real del viewport.
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                fijarScrollArriba(scroll, 2);
            }
        });

        botonFrente.addActionListener(e -> { vista.setVistaTrasera(false); marcarBotonesVista(); });
        botonEspalda.addActionListener(e -> { vista.setVistaTrasera(true); marcarBotonesVista(); });
        botonActivar.addActionListener(e -> activarSeleccionada());
        botonEliminar.addActionListener(e -> eliminarSeleccionada());
        casillaFina.addActionListener(e -> cambiarModeloSeleccion());
        marcarBotonesVista();

        refrescar();
    }

    // -------------------------------------------------------------- Encabezado

    private JPanel construirEncabezado() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel titulo = new JLabel("Apariencia");
        titulo.setFont(Tema.titulo());
        titulo.setForeground(Tema.TEXTO);
        JLabel subtitulo = new JLabel("Biblioteca de skins y vista previa del personaje");
        subtitulo.setFont(Tema.cuerpo());
        subtitulo.setForeground(Tema.TEXTO_SUAVE);
        subtitulo.setBorder(new EmptyBorder(2, 0, Tema.ESPACIO_MEDIO, 0));
        p.add(titulo);
        p.add(subtitulo);
        return p;
    }

    // ---------------------------------------------------------- Tarjeta preview

    private Tema.Tarjeta construirTarjetaPreview() {
        Tema.Tarjeta t = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));
        t.setPreferredSize(new Dimension(266, 10));
        // Ancho minimo real: sin esto, cuando el reparto de columnas del
        // GridBagLayout no puede satisfacer a las dos a la vez (la columna 0
        // pide 266 fijos, weightx=0), la implementacion de AWT a veces
        // encoge tambien las columnas de peso cero -- y esta era la unica
        // que se notaba, porque la casilla "Modelo fino" quedaba mas
        // apretada cuanto mas grande era la ventana.
        t.setMinimumSize(new Dimension(266, 10));

        JLabel titulo = new JLabel("Vista previa");
        titulo.setFont(Tema.subtitulo());
        titulo.setForeground(Tema.TEXTO);
        t.add(titulo, BorderLayout.NORTH);

        vista.setPreferredSize(new Dimension(200, 260));
        t.add(vista, BorderLayout.CENTER);

        JPanel sur = new JPanel();
        sur.setOpaque(false);
        sur.setLayout(new BoxLayout(sur, BoxLayout.Y_AXIS));

        JPanel filaBotones = new JPanel(new GridLayout(1, 2, Tema.ESPACIO_PEQUENO, 0));
        filaBotones.setOpaque(false);
        filaBotones.add(botonFrente);
        filaBotones.add(botonEspalda);
        filaBotones.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(filaBotones);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        casillaFina.setOpaque(false);
        casillaFina.setForeground(Tema.TEXTO_SUAVE);
        casillaFina.setFont(Tema.etiqueta());
        casillaFina.setAlignmentX(Component.LEFT_ALIGNMENT);
        casillaFina.setFocusPainted(false);
        sur.add(casillaFina);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        // En columna, no en fila: "Ya es la activa" y "Usar esta piel" no caben
        // a la mitad del ancho de esta tarjeta angosta sin truncarse con "...".
        JPanel filaAcciones = new JPanel();
        filaAcciones.setOpaque(false);
        filaAcciones.setLayout(new BoxLayout(filaAcciones, BoxLayout.Y_AXIS));
        botonActivar.setAlignmentX(Component.LEFT_ALIGNMENT);
        botonEliminar.setAlignmentX(Component.LEFT_ALIGNMENT);
        botonActivar.setMaximumSize(new Dimension(Integer.MAX_VALUE, botonActivar.getPreferredSize().height));
        botonEliminar.setMaximumSize(new Dimension(Integer.MAX_VALUE, botonEliminar.getPreferredSize().height));
        filaAcciones.add(botonActivar);
        filaAcciones.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
        filaAcciones.add(botonEliminar);
        filaAcciones.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(filaAcciones);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
        etiquetaSeleccion.setFont(Tema.etiqueta());
        etiquetaSeleccion.setForeground(Tema.TEXTO_DEBIL);
        etiquetaSeleccion.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(etiquetaSeleccion);

        t.add(sur, BorderLayout.SOUTH);
        return t;
    }

    // ------------------------------------------------------- Tarjeta biblioteca

    private Tema.Tarjeta construirTarjetaBiblioteca() {
        Tema.Tarjeta t = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        // GridBagLayout, no BorderLayout: con BorderLayout, si la columna se
        // estrecha (900 px de ancho de ventana) el titulo y el boton "Importar
        // PNG..." dejan de caber uno junto al otro y se SOLAPAN -- ninguno se
        // mueve de su sitio, BorderLayout no evita el solape. Con el titulo en
        // weightx=1/fill HORIZONTAL, su columna se encoge primero y como mucho
        // recorta el propio texto contra su borde derecho, pero nunca sobre el
        // boton, que siempre conserva su ancho propio a la derecha.
        JPanel norte = new JPanel(new GridBagLayout());
        norte.setOpaque(false);
        JLabel titulo = new JLabel("Biblioteca de skins");
        titulo.setFont(Tema.subtitulo());
        titulo.setForeground(Tema.TEXTO);
        GridBagConstraints gcTitulo = new GridBagConstraints();
        gcTitulo.gridx = 0;
        gcTitulo.gridy = 0;
        gcTitulo.weightx = 1;
        gcTitulo.fill = GridBagConstraints.HORIZONTAL;
        gcTitulo.anchor = GridBagConstraints.WEST;
        norte.add(titulo, gcTitulo);
        Tema.BotonSecundario botonImportar = new Tema.BotonSecundario("Importar PNG...");
        botonImportar.addActionListener(e -> importarSkin());
        GridBagConstraints gcBoton = new GridBagConstraints();
        gcBoton.gridx = 1;
        gcBoton.gridy = 0;
        gcBoton.weightx = 0;
        gcBoton.anchor = GridBagConstraints.EAST;
        gcBoton.insets = new java.awt.Insets(0, Tema.ESPACIO_MEDIO, 0, 0);
        norte.add(botonImportar, gcBoton);
        t.add(norte, BorderLayout.NORTH);

        listaBiblioteca.setOpaque(false);
        listaBiblioteca.setLayout(new BoxLayout(listaBiblioteca, BoxLayout.Y_AXIS));
        JScrollPane scrollLista = new JScrollPane(listaBiblioteca,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollLista.setOpaque(false);
        scrollLista.getViewport().setOpaque(false);
        scrollLista.setBorder(null);
        scrollLista.setPreferredSize(new Dimension(10, 220));
        scrollLista.getVerticalScrollBar().setUnitIncrement(16);
        t.add(scrollLista, BorderLayout.CENTER);

        JPanel sur = new JPanel();
        sur.setOpaque(false);
        sur.setLayout(new BoxLayout(sur, BoxLayout.Y_AXIS));
        sur.add(new Tema.Separador("Cuenta e instalacion"));

        etiquetaUuid.setFont(Tema.mono());
        etiquetaUuid.setForeground(Tema.TEXTO);
        etiquetaUuid.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(etiquetaUuid);

        JTextArea explicacionUuid = new TextoEnvolvente(
                "Este identificador sale de tu nombre de jugador y no cambia nunca: "
                + "por eso el inventario y el progreso se conservan aunque juegues desde otro equipo.");
        explicacionUuid.setFont(Tema.etiqueta());
        explicacionUuid.setForeground(Tema.TEXTO_DEBIL);
        explicacionUuid.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(explicacionUuid);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        JPanel filaInsignia = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filaInsignia.setOpaque(false);
        filaInsignia.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaInsignia.add(insigniaMod);
        sur.add(filaInsignia);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        textoModExplicacion.setFont(Tema.etiqueta());
        textoModExplicacion.setForeground(Tema.TEXTO_SUAVE);
        textoModExplicacion.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(textoModExplicacion);

        sur.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
        Tema.BotonSecundario botonCarpeta = new Tema.BotonSecundario("Abrir carpeta resourcepacks");
        botonCarpeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        botonCarpeta.addActionListener(e -> abrirCarpetaResourcepacks());
        sur.add(botonCarpeta);

        t.add(sur, BorderLayout.SOUTH);
        return t;
    }

    // ------------------------------------------------------------------ Accion

    /** Vuelve a leer disco (biblioteca, UUID, deteccion de mod) y redibuja todo. */
    public void refrescar() {
        etiquetaUuid.setText("UUID offline: " + uuidLegible());

        lista = servicio.listar();
        String activa = servicio.nombreActivo();
        if (nombreSeleccionado == null || lista.stream().noneMatch(s -> s.nombre.equals(nombreSeleccionado))) {
            nombreSeleccionado = (activa != null) ? activa : (lista.isEmpty() ? null : lista.get(0).nombre);
        }

        reconstruirListaBiblioteca(activa);
        actualizarSeleccionEnPantalla();
        actualizarDeteccionMod();
        revalidate();
        repaint();
        if (scrollCentro != null) fijarScrollArriba(scrollCentro, 3);
    }

    /**
     * Vuelve a dejar el scroll en (0,0) varias veces seguidas via invokeLater.
     * Hace falta mas de una pasada porque el alto de {@link TextoEnvolvente}
     * depende del ancho real del viewport, que solo se conoce despues de que
     * el layout ya corrio al menos una vez: cada pasada puede seguir
     * encolando otra igual, y una sola llamada puede quedar antes de que la
     * ultima de esas pasadas mueva el scroll.
     */
    private static void fijarScrollArriba(JScrollPane scroll, int vecesRestantes) {
        scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
        if (vecesRestantes > 0) {
            javax.swing.SwingUtilities.invokeLater(() -> fijarScrollArriba(scroll, vecesRestantes - 1));
        }
    }

    private String uuidLegible() {
        String nombre = (config.playerName != null && !config.playerName.isBlank()) ? config.playerName : "Jugador";
        UUID uuid = MinecraftLauncher.offlineUuid(nombre);
        return uuid.toString() + "  (" + nombre + ")";
    }

    private void reconstruirListaBiblioteca(String activa) {
        listaBiblioteca.removeAll();
        // ButtonGroup no expone un metodo para vaciar sus botones registrados,
        // asi que se crea uno nuevo en cada reconstruccion: la lista es corta,
        // no hay coste real.
        grupoSeleccion = new ButtonGroup();

        if (lista.isEmpty()) {
            JLabel vacio = new JLabel("<html>Todavia no hay ninguna skin guardada.<br>Importa un PNG de 64x64 (o 64x32) para empezar.</html>");
            vacio.setFont(Tema.etiqueta());
            vacio.setForeground(Tema.TEXTO_DEBIL);
            vacio.setBorder(new EmptyBorder(Tema.ESPACIO_MEDIO, 0, Tema.ESPACIO_MEDIO, 0));
            listaBiblioteca.add(vacio);
            return;
        }

        for (SkinsServicio.SkinInfo s : lista) {
            boolean esActiva = s.nombre.equals(activa);
            FilaSkin fila = new FilaSkin(s, esActiva);
            fila.addActionListener(e -> seleccionar(s.nombre));
            grupoSeleccion.add(fila);
            listaBiblioteca.add(fila);
            listaBiblioteca.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        }
    }

    private void seleccionar(String nombre) {
        nombreSeleccionado = nombre;
        actualizarSeleccionEnPantalla();
    }

    private SkinsServicio.SkinInfo seleccionActual() {
        if (nombreSeleccionado == null) return null;
        for (SkinsServicio.SkinInfo s : lista) {
            if (s.nombre.equals(nombreSeleccionado)) return s;
        }
        return null;
    }

    private void actualizarSeleccionEnPantalla() {
        SkinsServicio.SkinInfo sel = seleccionActual();
        String activa = servicio.nombreActivo();

        for (Component c : listaBiblioteca.getComponents()) {
            if (c instanceof FilaSkin fs) {
                fs.setSelected(fs.info.nombre.equals(nombreSeleccionado));
            }
        }

        if (sel == null) {
            vista.setSkin(null, false);
            etiquetaSeleccion.setText("Ninguna skin seleccionada");
            botonActivar.setEnabled(false);
            botonEliminar.setEnabled(false);
            actualizandoCasilla = true;
            casillaFina.setSelected(false);
            actualizandoCasilla = false;
            casillaFina.setEnabled(false);
            return;
        }

        BufferedImage img = SkinsServicio.cargarImagen(sel.ruta);
        vista.setSkin(img, sel.slim);
        boolean esActiva = sel.nombre.equals(activa);
        etiquetaSeleccion.setText(sel.nombre + (esActiva ? "  (activa)" : ""));
        botonActivar.setEnabled(!esActiva);
        botonActivar.setText(esActiva ? "Ya es la activa" : "Usar esta piel");
        botonEliminar.setEnabled(true);
        actualizandoCasilla = true;
        casillaFina.setSelected(sel.slim);
        actualizandoCasilla = false;
        casillaFina.setEnabled(true);
    }

    private void marcarBotonesVista() {
        boolean trasera = vista.isVistaTrasera();
        botonFrente.setEnabled(trasera);
        botonEspalda.setEnabled(!trasera);
    }

    private void importarSkin() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Elegir imagen de skin (PNG 64x64 o 64x32)");
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        int r = chooser.showOpenDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) return;
        File elegido = chooser.getSelectedFile();
        try {
            String nombreSugerido = elegido.getName();
            SkinsServicio.SkinInfo nueva = servicio.importar(elegido.toPath(), nombreSugerido);
            nombreSeleccionado = nueva.nombre;
            refrescar();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo importar", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Mensajes.explicar(ex), "No se pudo importar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void activarSeleccionada() {
        SkinsServicio.SkinInfo sel = seleccionActual();
        if (sel == null) return;
        try {
            servicio.marcarActiva(sel.nombre);
            refrescar();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Mensajes.explicar(ex), "No se pudo activar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminarSeleccionada() {
        SkinsServicio.SkinInfo sel = seleccionActual();
        if (sel == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Se borrara \"" + sel.nombre + "\" de la biblioteca. Esto no se puede deshacer.\n\n?Continuar?",
                "Eliminar skin", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        try {
            servicio.eliminar(sel.nombre);
            nombreSeleccionado = null;
            refrescar();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Mensajes.explicar(ex), "No se pudo eliminar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cambiarModeloSeleccion() {
        if (actualizandoCasilla) return;
        SkinsServicio.SkinInfo sel = seleccionActual();
        if (sel == null) return;
        try {
            servicio.setSlim(sel.nombre, casillaFina.isSelected());
            refrescar();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Mensajes.explicar(ex), "No se pudo guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirCarpetaResourcepacks() {
        Path dir = (config.gameDir != null && !config.gameDir.isBlank())
                ? java.nio.file.Paths.get(config.gameDir).resolve("resourcepacks") : null;
        if (dir == null) {
            JOptionPane.showMessageDialog(this, "No se ha detectado la carpeta del juego todavia.",
                    "Carpeta no disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                JOptionPane.showMessageDialog(this, "Ruta:\n" + dir, "Carpeta resourcepacks", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Mensajes.explicar(ex), "No se pudo abrir", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarDeteccionMod() {
        Path gameDir = (config.gameDir != null && !config.gameDir.isBlank())
                ? java.nio.file.Paths.get(config.gameDir) : null;
        SkinsServicio.ModSkinDetectado mod = SkinsServicio.detectarModDeSkins(gameDir);
        if (mod == null) {
            insigniaMod.setEstado(Tema.Estado.AVISO);
            insigniaMod.setTexto("Sin mod de skins instalado");
            textoModExplicacion.setText(
                    "Con cuenta offline, Minecraft no descarga skins de Mojang, y este pack no trae "
                    + "ningun mod de skins (como CustomSkinLoader). Por eso la vista previa de aqui no se "
                    + "reflejara dentro del juego: solo sirve para elegir y guardar. Anadir ese mod es una "
                    + "decision de Steven porque obliga a sincronizar servidor, torre y el equipo de Isa.");
        } else if (mod.habilitado) {
            insigniaMod.setEstado(Tema.Estado.EXITO);
            insigniaMod.setTexto("Mod detectado: " + mod.nombreArchivo);
            textoModExplicacion.setText(
                    "Se encontro " + mod.nombreArchivo + " activo en mods/. Este launcher todavia no escribe "
                    + "su configuracion de forma automatica (no se ha podido verificar el formato exacto que "
                    + "usa esa version); la skin elegida aqui queda guardada en la biblioteca, lista para "
                    + "apuntarla a mano en la carpeta que pida ese mod.");
        } else {
            insigniaMod.setEstado(Tema.Estado.AVISO);
            insigniaMod.setTexto("Mod presente pero desactivado: " + mod.nombreArchivo);
            textoModExplicacion.setText(
                    "Hay un " + mod.nombreArchivo + " en mods-disabled/, pero esta desactivado. Mientras siga "
                    + "ahi, el juego no lo carga y la skin solo se ve en esta vista previa.");
        }
    }

    // ================================================== Panel que respeta el ancho del viewport
    //
    // Ver el comentario junto a su unico uso (arriba, en el constructor): sin
    // esto, un JScrollPane con scroll horizontal desactivado deja que su
    // contenido se salga del ancho visible sin avisar.
    private static final class PanelAnchoDeViewport extends JPanel implements Scrollable {
        PanelAnchoDeViewport(LayoutManager gestor) { super(gestor); }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle vista, int orientacion, int direccion) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle vista, int orientacion, int direccion) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ============================================================ Texto que envuelve de verdad
    //
    // Un JLabel con <html> (o un JTextArea con salto de linea activado) sin mas
    // calculan su ancho preferido como si el texto fuera una sola linea sin
    // envolver: eso empuja a la tarjeta entera a pedir un ancho enorme y el
    // GridBagLayout de este panel, con el scroll horizontal desactivado a
    // proposito, lo recorta en silencio (asi se veian las frases cortadas en
    // el borde de la ventana). Este componente fuerza su propio ancho al del
    // contenedor real antes de calcular la altura envuelta, rompiendo ese
    // ciclo con el truco estandar de Swing: fijar el tamano con setSize()
    // antes de preguntar por el preferido.
    private static final class TextoEnvolvente extends JTextArea {
        TextoEnvolvente(String texto) {
            super(texto);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
        }

        @Override public Dimension getPreferredSize() {
            java.awt.Container padre = getParent();
            int ancho = (padre != null && padre.getWidth() > 0) ? padre.getWidth() : 260;
            setSize(new Dimension(ancho, Short.MAX_VALUE));
            Dimension d = super.getPreferredSize();
            return new Dimension(ancho, d.height);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    // ============================================================== Fila de la biblioteca

    /** Una fila seleccionable de la biblioteca: miniatura de la cabeza + nombre + estado. */
    private static final class FilaSkin extends JToggleButton {
        final SkinsServicio.SkinInfo info;
        private BufferedImage miniatura;
        private boolean sobre = false;

        FilaSkin(SkinsServicio.SkinInfo info, boolean activa) {
            this.info = info;
            setLayout(null);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setPreferredSize(new Dimension(220, 44));

            BufferedImage skin = SkinsServicio.cargarImagen(info.ruta);
            if (skin != null && skin.getWidth() == 64 && (skin.getHeight() == 64 || skin.getHeight() == 32)) {
                BufferedImage cara = skin.getSubimage(8, 8, 8, 8);
                BufferedImage capa = skin.getSubimage(40, 8, 8, 8);
                miniatura = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = miniatura.createGraphics();
                g.drawImage(cara, 0, 0, null);
                g.drawImage(capa, 0, 0, null);
                g.dispose();
            }

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { sobre = false; repaint(); }
            });

            String estado = activa ? "  -  activa" : "";
            setToolTipText(info.nombre + estado);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth(), h = getHeight();

            if (isSelected()) {
                g2.setColor(Tema.ACENTO_APAGADO);
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
                g2.setColor(Tema.ACENTO);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
            } else if (sobre) {
                g2.setColor(Tema.SUPERFICIE_ELEVADA);
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
            }

            int tam = 30;
            int mx = 7, my = (h - tam) / 2;
            g2.setColor(Tema.SUPERFICIE_ELEVADA);
            g2.fillRoundRect(mx, my, tam, tam, 6, 6);
            if (miniatura != null) {
                Object viejo = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(miniatura, mx + 2, my + 2, tam - 4, tam - 4, null);
                if (viejo != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, viejo);
            } else {
                g2.setColor(Tema.TEXTO_DEBIL);
                g2.setFont(Tema.etiqueta());
                FontMetrics fm = g2.getFontMetrics();
                String signo = "?";
                g2.drawString(signo, mx + tam / 2 - fm.stringWidth(signo) / 2, my + tam / 2 + fm.getAscent() / 2 - 1);
            }

            g2.setFont(Tema.cuerpo());
            g2.setColor(isSelected() ? Tema.TEXTO : Tema.TEXTO_SUAVE);
            FontMetrics fm = g2.getFontMetrics();
            int ty = h / 2 + fm.getAscent() / 2 - 2;
            String nombreCorto = recortarTexto(info.nombre, fm, w - mx - tam - 24);
            g2.drawString(nombreCorto, mx + tam + 10, ty);

            if (info.slim) {
                g2.setFont(Tema.etiqueta());
                g2.setColor(Tema.TEXTO_DEBIL);
                String marca = "fino";
                FontMetrics fme = g2.getFontMetrics();
                g2.drawString(marca, w - fme.stringWidth(marca) - 10, ty);
            }

            g2.dispose();
        }

        private static String recortarTexto(String s, FontMetrics fm, int maxAncho) {
            if (fm.stringWidth(s) <= maxAncho) return s;
            String base = s;
            while (base.length() > 1 && fm.stringWidth(base + "...") > maxAncho) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "...";
        }
    }

    // ================================================================= Vista previa

    /**
     * Compone y dibuja el "muneco" de la skin recortando las regiones estandar
     * de un PNG de Minecraft (cabeza, cuerpo, brazos, piernas y su capa
     * exterior) con Java2D puro. Sin perspectiva 3D: es una vista plana, de
     * frente o de espalda, con escalado a pixel limpio (sin difuminar).
     */
    private static final class VistaSkin extends JPanel {
        private BufferedImage skin;
        private boolean slim;
        private boolean trasera;
        private BufferedImage doll;

        VistaSkin() {
            setOpaque(false);
        }

        void setSkin(BufferedImage skin, boolean slim) {
            this.skin = skin;
            this.slim = slim;
            recomponer();
        }

        void setVistaTrasera(boolean trasera) {
            this.trasera = trasera;
            recomponer();
        }

        boolean isVistaTrasera() { return trasera; }

        private void recomponer() {
            doll = (skin != null) ? componerMuneco(skin, slim, trasera) : null;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth(), h = getHeight();

            g2.setColor(Tema.SUPERFICIE_ELEVADA);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_MEDIO, Tema.RADIO_MEDIO));
            g2.setColor(Tema.BORDE_SUAVE);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, Tema.RADIO_MEDIO, Tema.RADIO_MEDIO));

            if (doll == null) {
                dibujarSiluetaVacia(g2, w, h);
                g2.dispose();
                return;
            }

            int margen = 18;
            int areaW = w - margen * 2;
            int areaH = h - margen * 2;
            int escala = Math.max(2, Math.min(areaW / doll.getWidth(), areaH / doll.getHeight()));
            int dw = doll.getWidth() * escala;
            int dh = doll.getHeight() * escala;
            int dx = (w - dw) / 2;
            int dy = (h - dh) / 2;

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(doll, dx, dy, dw, dh, null);
            g2.dispose();
        }

        private void dibujarSiluetaVacia(Graphics2D g2, int w, int h) {
            g2.setColor(Tema.BORDE_SUAVE);
            int cw = Math.min(w, h) / 3;
            int cx = w / 2, cy = h / 2 - cw;
            g2.fillOval(cx - cw / 2, cy - cw / 2, cw, cw);
            g2.fillRoundRect(cx - cw / 2, cy + cw / 3, cw, (int) (cw * 1.6), cw / 3, cw / 3);

            g2.setFont(Tema.etiqueta());
            g2.setColor(Tema.TEXTO_DEBIL);
            String texto = "Sin skin seleccionada";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, (w - fm.stringWidth(texto)) / 2, h - 16);
        }

        // ---------------------------------------------------- Composicion del muneco

        private static BufferedImage componerMuneco(BufferedImage skin, boolean slim, boolean back) {
            boolean nuevoFormato = skin.getHeight() >= 64;
            int armW = slim ? 3 : 4;

            BufferedImage head = componer(8, 8,
                    tile(skin, back ? 24 : 8, 8, 8, 8),
                    tile(skin, (back ? 24 : 8) + 32, 8, 8, 8));

            BufferedImage body = componer(8, 12,
                    tile(skin, back ? 32 : 20, 20, 8, 12),
                    nuevoFormato ? tile(skin, back ? 32 : 20, 36, 8, 12) : null);

            BufferedImage armA = componer(armW, 12,
                    tile(skin, back ? 52 : 40, 20, armW, 12),
                    nuevoFormato ? tile(skin, back ? 52 : 40, 36, armW, 12) : null);

            BufferedImage armB;
            if (nuevoFormato) {
                armB = componer(armW, 12,
                        tile(skin, (back ? 44 : 36), 52, armW, 12),
                        tile(skin, (back ? 60 : 48), 52, armW, 12));
            } else {
                armB = espejar(armA);
            }

            BufferedImage legA = componer(4, 12,
                    tile(skin, back ? 12 : 4, 20, 4, 12),
                    nuevoFormato ? tile(skin, back ? 12 : 4, 36, 4, 12) : null);

            BufferedImage legB;
            if (nuevoFormato) {
                legB = componer(4, 12,
                        tile(skin, back ? 28 : 20, 52, 4, 12),
                        tile(skin, back ? 12 : 4, 52, 4, 12));
            } else {
                legB = espejar(legA);
            }

            int ancho = armW + 8 + armW;
            int alto = 8 + 12 + 12;
            BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = lienzo.createGraphics();
            g.drawImage(head, (ancho - 8) / 2, 0, null);
            g.drawImage(armA, 0, 8, null);
            g.drawImage(body, armW, 8, null);
            g.drawImage(armB, armW + 8, 8, null);
            g.drawImage(legA, armW, 20, null);
            g.drawImage(legB, armW + 4, 20, null);
            g.dispose();
            return lienzo;
        }

        private static BufferedImage tile(BufferedImage skin, int x, int y, int w, int h) {
            if (skin == null) return null;
            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > skin.getWidth() || y + h > skin.getHeight()) return null;
            return skin.getSubimage(x, y, w, h);
        }

        private static BufferedImage componer(int w, int h, BufferedImage base, BufferedImage overlay) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            if (base != null) g.drawImage(base, 0, 0, null);
            if (overlay != null) g.drawImage(overlay, 0, 0, null);
            g.dispose();
            return out;
        }

        private static BufferedImage espejar(BufferedImage im) {
            int w = im.getWidth(), h = im.getHeight();
            AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-w, 0);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            op.filter(im, out);
            return out;
        }
    }
}
