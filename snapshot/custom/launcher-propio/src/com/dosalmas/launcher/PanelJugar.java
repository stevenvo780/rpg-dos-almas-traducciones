package com.dosalmas.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Pantalla que ve Isa: identidad del modpack, nombre de jugador, avatar
 * generado a partir del UUID offline, estado de la instalacion y el boton
 * JUGAR. Es el UNICO panel visible en modo simple y, en modo avanzado, el
 * mismo panel reutilizado dentro del CardLayout.
 *
 * Este panel se encarga tambien de lanzar la partida (reutiliza
 * MinecraftLauncher/Verificador/Registro): reporta cada linea de la consola
 * del juego a traves de {@code alLineaDeConsola} para que la ventana la
 * reenvie a PanelRegistros, sin mantener aqui su propia consola.
 */
public final class PanelJugar extends JPanel {

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private Config config;
    private final Runnable alGuardarConfig;
    private final Runnable alIrAAjustes;
    private final Consumer<String> alLineaDeConsola;

    private List<Verificador.Problema> problemas = List.of();
    private LaunchWorker workerActual;
    private volatile Process procesoJuego;

    private Avatar avatar;
    private Tema.CampoTexto campoNombre;
    private JTextArea reglaNombre;
    private Tema.Insignia insigniaEstado;
    private JPanel panelProblemas;
    private Tema.BotonSecundario botonDetalles;
    private boolean detallesVisibles = false;
    private Tema.BotonPrimario botonJugar;
    private JProgressBar progreso;
    private JLabel estadoLinea;
    private JLabel datoVersion;
    private JLabel datoMods;
    private JLabel datoMemoria;

    public PanelJugar(Config config,
                       Runnable alGuardarConfig,
                       Runnable alIrAAjustes,
                       Consumer<String> alLineaDeConsola) {
        super(new BorderLayout());
        this.config = config;
        this.alGuardarConfig = alGuardarConfig;
        this.alIrAAjustes = alIrAAjustes;
        this.alLineaDeConsola = alLineaDeConsola;
        setOpaque(false);
        construir();
        problemas = Verificador.revisar(config.toProfile());
        actualizarEstadoInstalacion();
        validarNombre();
    }

    // --------------------------------------------------------------- Layout

    private void construir() {
        add(construirCabecera(), BorderLayout.NORTH);

        JPanel centro = construirCentro();
        JPanel envoltorio = new JPanel(new GridBagLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(centro, new GridBagConstraints());
        add(envoltorio, BorderLayout.CENTER);
    }

    private JComponent construirCabecera() {
        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.setBorder(new EmptyBorder(Tema.ESPACIO_PEQUENO, 0, 0, 0));

        // Acceso permanente a los ajustes. Sin el, en modo simple no habria
        // forma de llegar a Ajustes (ni de activar el modo avanzado, que es
        // donde viven las demas secciones): la unica puerta era el boton que
        // aparece cuando la instalacion falla, asi que con todo correcto el
        // launcher se quedaba encerrado en esta pantalla.
        JPanel barraSuperior = new JPanel(new BorderLayout());
        barraSuperior.setOpaque(false);
        barraSuperior.setBorder(new EmptyBorder(0, 0, Tema.ESPACIO_PEQUENO, 0));
        barraSuperior.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        Tema.BotonSecundario ajustes = new Tema.BotonSecundario("Ajustes");
        ajustes.setToolTipText("Memoria, rutas, mods, apariencia y registros");
        ajustes.setFocusable(false);   // el foco inicial es para el nombre
        ajustes.addActionListener(e -> { if (alIrAAjustes != null) alIrAAjustes.run(); });
        barraSuperior.add(ajustes, BorderLayout.EAST);
        cabecera.add(barraSuperior);

        Emblema emblema = new Emblema();
        emblema.setAlignmentX(Component.CENTER_ALIGNMENT);
        cabecera.add(emblema);

        cabecera.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        JLabel titulo = new JLabel("RPG Dos Almas");
        titulo.setFont(Tema.titulo());
        titulo.setForeground(Tema.TEXTO);
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        cabecera.add(titulo);

        JLabel subtitulo = new JLabel("Modpack privado -- Minecraft 1.20.1");
        subtitulo.setFont(Tema.etiqueta());
        subtitulo.setForeground(Tema.TEXTO_DEBIL);
        subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        cabecera.add(subtitulo);

        return cabecera;
    }

    private JPanel construirCentro() {
        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE,
                Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE));

        // ------------------------------------------------- nombre + avatar
        //
        // GridBagLayout, no BoxLayout X_AXIS: con BoxLayout, esta fila se
        // estiraba al ancho completo de "centro" (su unico hijo sin maximo
        // fijo, la columna de texto, absorbia todo el sobrante) y el grupo
        // avatar+campo quedaba pegado a la izquierda en vez de centrado -- 80
        // px descentrado, medido en captura. Con GridBagLayout y weightx=0 en
        // las dos columnas, el conjunto se centra solo cuando sobra ancho.
        //
        // El avatar se ancla ademas SOLO contra la etiqueta+campo (gridheight
        // 2, sin incluir reglaNombre): antes se centraba contra la columna
        // entera (etiqueta+campo+texto de ayuda) y quedaba ~15 px por debajo
        // del centro real del campo de texto.
        JPanel filaNombre = new JPanel(new GridBagLayout());
        filaNombre.setOpaque(false);
        filaNombre.setAlignmentX(Component.CENTER_ALIGNMENT);

        avatar = new Avatar();
        GridBagConstraints gcAvatar = new GridBagConstraints();
        gcAvatar.gridx = 0;
        gcAvatar.gridy = 0;
        gcAvatar.gridheight = 2;
        gcAvatar.anchor = GridBagConstraints.CENTER;
        gcAvatar.insets = new java.awt.Insets(0, 0, 0, Tema.ESPACIO_MEDIO);
        filaNombre.add(avatar, gcAvatar);

        JLabel etiquetaNombre = new JLabel("NOMBRE DE JUGADOR");
        etiquetaNombre.setFont(Tema.etiqueta());
        etiquetaNombre.setForeground(Tema.TEXTO_DEBIL);
        GridBagConstraints gcEtiqueta = new GridBagConstraints();
        gcEtiqueta.gridx = 1;
        gcEtiqueta.gridy = 0;
        gcEtiqueta.anchor = GridBagConstraints.WEST;
        gcEtiqueta.insets = new java.awt.Insets(0, 0, 4, 0);
        filaNombre.add(etiquetaNombre, gcEtiqueta);

        campoNombre = new Tema.CampoTexto(14);
        campoNombre.setText(config.playerName);
        campoNombre.setPreferredSize(new Dimension(220, 40));
        campoNombre.setMinimumSize(new Dimension(220, 40));
        campoNombre.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { validarNombre(); }
            @Override public void removeUpdate(DocumentEvent e) { validarNombre(); }
            @Override public void changedUpdate(DocumentEvent e) { validarNombre(); }
        });
        GridBagConstraints gcCampo = new GridBagConstraints();
        gcCampo.gridx = 1;
        gcCampo.gridy = 1;
        gcCampo.anchor = GridBagConstraints.WEST;
        filaNombre.add(campoNombre, gcCampo);

        // JTextArea con salto de linea, no JLabel: un JLabel con html calcula
        // su ancho mal dentro de un BoxLayout y el mensaje largo de error
        // acababa recortado en vez de envuelto. Con lineWrap esto es fiable.
        reglaNombre = new JTextArea(" ");
        reglaNombre.setFont(Tema.etiqueta());
        reglaNombre.setEditable(false);
        reglaNombre.setFocusable(false);
        reglaNombre.setOpaque(false);
        reglaNombre.setLineWrap(true);
        reglaNombre.setWrapStyleWord(true);
        reglaNombre.setBorder(null);
        reglaNombre.setPreferredSize(new Dimension(220, 46));
        reglaNombre.setMinimumSize(new Dimension(220, 46));
        GridBagConstraints gcRegla = new GridBagConstraints();
        gcRegla.gridx = 1;
        gcRegla.gridy = 2;
        gcRegla.anchor = GridBagConstraints.WEST;
        gcRegla.insets = new java.awt.Insets(4, 0, 0, 0);
        filaNombre.add(reglaNombre, gcRegla);

        // Sin esto, un BoxLayout Y_AXIS (el de "centro") puede seguir
        // estirando filaNombre a todo el ancho antes de que el centrado por
        // sobrante de GridBagLayout entre en juego: fijar el maximo a su
        // propio preferido es lo que garantiza que BoxLayout la centre en
        // vez de estirarla.
        Dimension prefFila = filaNombre.getPreferredSize();
        filaNombre.setMaximumSize(prefFila);
        centro.add(filaNombre);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_GRANDE));

        // ------------------------------------------------------- instalacion
        insigniaEstado = new Tema.Insignia("Comprobando...", Tema.Estado.NEUTRO);
        insigniaEstado.setAlignmentX(Component.CENTER_ALIGNMENT);
        centro.add(insigniaEstado);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_XS));

        botonDetalles = new Tema.BotonSecundario("Ver detalles");
        botonDetalles.setAlignmentX(Component.CENTER_ALIGNMENT);
        botonDetalles.addActionListener(e -> alternarDetalles());
        botonDetalles.setVisible(false);
        centro.add(botonDetalles);

        panelProblemas = new JPanel();
        panelProblemas.setOpaque(false);
        panelProblemas.setLayout(new BoxLayout(panelProblemas, BoxLayout.Y_AXIS));
        panelProblemas.setAlignmentX(Component.CENTER_ALIGNMENT);
        panelProblemas.setVisible(false);
        // 420 px dejaban el titulo del problema recortado a mitad de palabra
        // ("No se encontro la carpeta de recursos (asse...").
        panelProblemas.setMaximumSize(new Dimension(470, 4000));
        centro.add(panelProblemas);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_GRANDE));

        // --------------------------------------------------------- jugar
        botonJugar = new Tema.BotonPrimario("JUGAR");
        botonJugar.setAlignmentX(Component.CENTER_ALIGNMENT);
        botonJugar.setFont(Tema.subtitulo());
        botonJugar.setPreferredSize(new Dimension(220, 54));
        botonJugar.setMaximumSize(new Dimension(220, 54));
        botonJugar.addActionListener(e -> onJugar());
        centro.add(botonJugar);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        progreso = new JProgressBar();
        progreso.setAlignmentX(Component.CENTER_ALIGNMENT);
        progreso.setMaximumSize(new Dimension(260, 6));
        progreso.setPreferredSize(new Dimension(260, 6));
        progreso.setBorderPainted(false);
        progreso.setForeground(Tema.ACENTO);
        progreso.setBackground(Tema.SUPERFICIE_ELEVADA);
        centro.add(progreso);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        estadoLinea = new JLabel("Listo para jugar.");
        estadoLinea.setFont(Tema.etiqueta());
        estadoLinea.setForeground(Tema.TEXTO_SUAVE);
        estadoLinea.setAlignmentX(Component.CENTER_ALIGNMENT);
        centro.add(estadoLinea);

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_GRANDE));

        centro.add(new EnvoltorioAncho(new Tema.Separador(), 320));

        centro.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        JPanel datos = new JPanel(new FlowLayout(FlowLayout.CENTER, Tema.ESPACIO_GRANDE, 0));
        datos.setOpaque(false);
        datos.setAlignmentX(Component.CENTER_ALIGNMENT);
        datoVersion = etiquetaDato("");
        datoMods = etiquetaDato("");
        datoMemoria = etiquetaDato("");
        datos.add(datoVersion);
        datos.add(datoMods);
        datos.add(datoMemoria);
        centro.add(datos);

        return centro;
    }

    private JLabel etiquetaDato(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.etiqueta());
        l.setForeground(Tema.TEXTO_DEBIL);
        return l;
    }

    /** Envuelve un componente para limitarle el ancho dentro de un BoxLayout vertical. */
    private static final class EnvoltorioAncho extends JPanel {
        EnvoltorioAncho(JComponent hijo, int ancho) {
            super(new BorderLayout());
            setOpaque(false);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setMaximumSize(new Dimension(ancho, hijo.getPreferredSize().height));
            setPreferredSize(new Dimension(ancho, hijo.getPreferredSize().height));
            add(hijo, BorderLayout.CENTER);
        }
    }

    // ------------------------------------------------------------ Refresco

    /** Vuelve a comprobar la instalacion y refresca los datos discretos. */
    public void refrescar() {
        problemas = Verificador.revisar(config.toProfile());
        actualizarEstadoInstalacion();
        actualizarDatos();
        validarNombre();
    }

    private void actualizarDatos() {
        datoVersion.setText("Version: " + safe(config.versionId));
        datoMods.setText("Mods activos: " + contarMods());
        int max = config.maxMemoryMb;
        datoMemoria.setText("Memoria: " + (max > 0 ? max + " MB" : "sin definir"));
    }

    private String safe(String s) { return (s == null || s.isBlank()) ? "sin detectar" : s; }

    private int contarMods() {
        if (config.gameDir == null || config.gameDir.isBlank()) return 0;
        Path mods = Paths.get(config.gameDir).resolve("mods");
        if (!Files.isDirectory(mods)) return 0;
        try (var s = Files.list(mods)) {
            return (int) s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void actualizarEstadoInstalacion() {
        boolean bloqueado = Verificador.hayBloqueantes(problemas);
        int bloqueantes = (int) problemas.stream().filter(Verificador.Problema::bloquea).count();
        int avisos = problemas.size() - bloqueantes;

        if (bloqueado) {
            insigniaEstado.setEstado(Tema.Estado.PELIGRO);
            insigniaEstado.setTexto("Falta algo (" + bloqueantes + ")");
        } else if (!problemas.isEmpty()) {
            insigniaEstado.setEstado(Tema.Estado.AVISO);
            insigniaEstado.setTexto("Listo, con avisos (" + avisos + ")");
        } else {
            insigniaEstado.setEstado(Tema.Estado.EXITO);
            insigniaEstado.setTexto("Instalacion lista");
        }

        // La linea de estado no debe quedar congelada en "Listo para jugar"
        // mientras la instalacion esta rota; solo se toca aqui si no hay una
        // partida en curso (eso lo controla onJugar/onLanzamientoTerminado).
        if (workerActual == null) {
            if (bloqueado) {
                estadoLinea.setText("Falta algo para poder jugar.");
                estadoLinea.setForeground(Tema.PELIGRO);
            } else if (!problemas.isEmpty()) {
                estadoLinea.setText("Listo, con avisos.");
                estadoLinea.setForeground(Tema.TEXTO_SUAVE);
            } else {
                estadoLinea.setText("Listo para jugar.");
                estadoLinea.setForeground(Tema.TEXTO_SUAVE);
            }
        }

        botonDetalles.setVisible(!problemas.isEmpty());
        if (problemas.isEmpty()) {
            panelProblemas.setVisible(false);
            detallesVisibles = false;
        }
        reconstruirPanelProblemas();
        if (bloqueado && !detallesVisibles) alternarDetalles();

        actualizarDatos();
        actualizarBotonJugar();
        revalidate();
        repaint();
    }

    private void alternarDetalles() {
        detallesVisibles = !detallesVisibles;
        panelProblemas.setVisible(detallesVisibles);
        botonDetalles.setText(detallesVisibles ? "Ocultar detalles" : "Ver detalles");
        revalidate();
        repaint();
        Component ventana = SwingUtilities.getWindowAncestor(this);
        if (ventana != null) ventana.revalidate();
    }

    private void reconstruirPanelProblemas() {
        panelProblemas.removeAll();
        for (int i = 0; i < problemas.size(); i++) {
            Verificador.Problema p = problemas.get(i);
            panelProblemas.add(tarjetaProblema(p));
            if (i < problemas.size() - 1) panelProblemas.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
        }
        if (!problemas.isEmpty()) {
            panelProblemas.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
            Tema.BotonSecundario abrirAjustes = new Tema.BotonSecundario("Abrir Ajustes");
            abrirAjustes.setAlignmentX(Component.CENTER_ALIGNMENT);
            abrirAjustes.addActionListener(e -> { if (alIrAAjustes != null) alIrAAjustes.run(); });
            panelProblemas.add(abrirAjustes);
        }
    }

    private JComponent tarjetaProblema(Verificador.Problema p) {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(Tema.ESPACIO_XS, Tema.ESPACIO_XS));
        tarjeta.setAlignmentX(Component.CENTER_ALIGNMENT);
        tarjeta.setBorder(new EmptyBorder(Tema.ESPACIO_PEQUENO, Tema.ESPACIO_MEDIO,
                Tema.ESPACIO_PEQUENO, Tema.ESPACIO_MEDIO));

        JPanel columna = new JPanel();
        columna.setOpaque(false);
        columna.setLayout(new BoxLayout(columna, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel((p.bloquea() ? "✖  " : "⚠  ") + p.titulo);
        titulo.setFont(Tema.cuerpoNegrita());
        titulo.setForeground(p.bloquea() ? Tema.PELIGRO : Tema.AVISO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        columna.add(titulo);

        if (p.detalle != null && !p.detalle.isEmpty()) {
            columna.add(Box.createVerticalStrut(2));
            columna.add(textoEnvolvente(p.detalle, Tema.TEXTO_SUAVE));
        }
        if (p.accion != null && !p.accion.isEmpty()) {
            columna.add(Box.createVerticalStrut(2));
            columna.add(textoEnvolvente("→ " + p.accion, Tema.ACENTO));
        }

        tarjeta.add(columna, BorderLayout.CENTER);
        return tarjeta;
    }

    private JTextArea textoEnvolvente(String texto, Color color) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(Tema.etiqueta());
        area.setForeground(color);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setBorder(null);
        return area;
    }

    // ------------------------------------------------------------ Nombre

    private void validarNombre() {
        String nombre = campoNombre.getText().trim();
        boolean valido = NOMBRE_VALIDO.matcher(nombre).matches();
        campoNombre.setError(!valido);
        reglaNombre.setForeground(valido ? Tema.TEXTO_DEBIL : Tema.PELIGRO);
        reglaNombre.setText(valido
                ? "3 a 16 caracteres: letras sin tilde, numeros y guion bajo (_)"
                : "Debe tener 3 a 16 caracteres. Solo letras sin tilde, numeros y guion bajo, sin espacios.");
        avatar.actualizar(nombre.isEmpty() ? "?" : nombre);
        actualizarBotonJugar();
    }

    private void actualizarBotonJugar() {
        String nombre = campoNombre.getText().trim();
        boolean nombreValido = NOMBRE_VALIDO.matcher(nombre).matches();
        boolean instalacionOk = !Verificador.hayBloqueantes(problemas);
        if (workerActual != null) return; // no tocar mientras se juega
        botonJugar.setEnabled(nombreValido && instalacionOk);
        botonJugar.setToolTipText(!nombreValido
                ? "Escribe un nombre de 3 a 16 caracteres: letras, numeros y _"
                : (!instalacionOk ? "Falta algo en la instalacion: pulsa \"Ver detalles\" o Ajustes" : null));
    }

    // ------------------------------------------------------------ Lanzar

    private void onJugar() {
        String nombre = campoNombre.getText().trim();
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) return;

        problemas = Verificador.revisar(config.toProfile());
        actualizarEstadoInstalacion();
        if (Verificador.hayBloqueantes(problemas)) return;

        config.playerName = nombre;
        try {
            config.save();
        } catch (IOException e) {
            // no impide jugar: se sigue aunque no se pudiera persistir
        }
        if (alGuardarConfig != null) alGuardarConfig.run();

        campoNombre.setEnabled(false);
        botonJugar.setEnabled(false);
        botonJugar.setText("Jugando...");
        progreso.setIndeterminate(true);
        estadoLinea.setText("Iniciando " + config.versionId + "...");
        estadoLinea.setForeground(Tema.TEXTO_SUAVE);
        if (alLineaDeConsola != null) {
            alLineaDeConsola.accept("== Lanzando " + config.versionId + " como '" + nombre + "' ==");
        }

        workerActual = new LaunchWorker(config.toProfile());
        workerActual.execute();
    }

    /** Si el proceso del juego sigue vivo; la ventana lo consulta antes de cerrarse. */
    public boolean juegoEnEjecucion() {
        Process p = procesoJuego;
        return p != null && p.isAlive();
    }

    private void onLanzamientoTerminado(LaunchWorker worker) {
        workerActual = null;
        campoNombre.setEnabled(true);
        botonJugar.setText("JUGAR");
        progreso.setIndeterminate(false);
        progreso.setValue(0);
        actualizarBotonJugar();
        try {
            int codigo = worker.get();
            if (alLineaDeConsola != null) {
                alLineaDeConsola.accept("");
                alLineaDeConsola.accept("== El juego termino con codigo " + codigo + " ==");
            }
            if (codigo == 0) {
                estadoLinea.setText("Listo para jugar.");
                estadoLinea.setForeground(Tema.TEXTO_SUAVE);
                return;
            }
            estadoLinea.setText("El juego termino con codigo " + codigo + ".");
            estadoLinea.setForeground(Tema.PELIGRO);
            mostrarSalidaAnomala(codigo, worker.segundosTranscurridos(), worker.archivoLog());
        } catch (Exception e) {
            Throwable causa = (e.getCause() != null) ? e.getCause() : e;
            estadoLinea.setText("Ocurrio un error al lanzar el juego.");
            estadoLinea.setForeground(Tema.PELIGRO);
            if (causa instanceof InstalacionIncompletaException) {
                problemas = ((InstalacionIncompletaException) causa).problemas();
                actualizarEstadoInstalacion();
            } else {
                mostrarError("Error al lanzar el juego", causa);
            }
        }
    }

    private void mostrarSalidaAnomala(int codigo, long segundos, Path log) {
        StringBuilder sb = new StringBuilder(Mensajes.explicarSalida(codigo, segundos));
        if (log != null) sb.append("\n\nRegistro completo: ").append(log);

        JTextArea area = new JTextArea(sb.toString(), 12, 56);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);

        Object[] opciones = (log != null)
                ? new Object[] {"Copiar ruta del registro", "Cerrar"}
                : new Object[] {"Cerrar"};
        int r = JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor(this),
                new JScrollPane(area), "El juego se cerro de forma inesperada",
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, opciones, opciones[opciones.length - 1]);
        if (log != null && r == 0) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(log.toString()), null);
        }
    }

    private void mostrarError(String titulo, Throwable t) {
        String texto = Mensajes.explicar(t);
        JTextArea area = new JTextArea(texto, 10, 56);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                new JScrollPane(area), titulo, JOptionPane.ERROR_MESSAGE);
    }

    // -------------------------------------------------------------- Worker

    private final class LaunchWorker extends SwingWorker<Integer, String> {
        private final MinecraftLauncher.Profile profile;
        private long inicio;
        private Path archivoLog;

        LaunchWorker(MinecraftLauncher.Profile profile) { this.profile = profile; }

        long segundosTranscurridos() { return (inicio == 0) ? -1 : (System.currentTimeMillis() - inicio) / 1000; }

        Path archivoLog() { return archivoLog; }

        @Override
        protected Integer doInBackground() throws Exception {
            MinecraftLauncher launcher = new MinecraftLauncher(profile);
            List<String> cmd = launcher.buildCommand();
            inicio = System.currentTimeMillis();

            try (Registro registro = Registro.abrir(profile, cmd)) {
                archivoLog = registro.archivo();
                Process proceso = launcher.launch();
                procesoJuego = proceso;

                // launch() deja la configuracion compartida como debe estar
                // justo antes de arrancar. Se cuenta lo que cambio para que no
                // sea magia invisible.
                Sincronizador.Resultado sinc = launcher.ultimaSincronizacion();
                if (sinc.huboCambios()) {
                    publish("== Configuracion compartida aplicada antes de arrancar ==");
                    registro.linea("== Configuracion compartida aplicada ==");
                    for (String c : sinc.cambios) {
                        publish("   " + c);
                        registro.linea("   " + c);
                    }
                }
                for (String a : sinc.avisos) {
                    publish("   aviso: " + a);
                    registro.linea("   aviso: " + a);
                }
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                    String linea;
                    while ((linea = r.readLine()) != null) {
                        registro.linea(linea);
                        publish(linea);
                    }
                }
                int codigo = proceso.waitFor();
                registro.cierre(codigo);
                return codigo;
            } finally {
                procesoJuego = null;
            }
        }

        @Override
        protected void process(List<String> chunks) {
            if (alLineaDeConsola == null) return;
            for (String linea : chunks) alLineaDeConsola.accept(linea);
        }

        @Override
        protected void done() { onLanzamientoTerminado(this); }
    }

    // ------------------------------------------------------------- Emblema

    /** Dos formas entrelazadas (dos "almas") pintadas con Java2D, sin imagenes. */
    private static final class Emblema extends JComponent {
        private static final int TAMANO = 64;

        Emblema() {
            setOpaque(false);
            setPreferredSize(new Dimension(TAMANO, TAMANO));
            setMaximumSize(new Dimension(TAMANO, TAMANO));
            setMinimumSize(new Dimension(TAMANO, TAMANO));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);

            int w = getWidth();
            int h = getHeight();
            int d = (int) (w * 0.62);
            int ox1 = (w - d) / 2 - (int) (d * 0.22);
            int oy1 = (h - d) / 2 - (int) (d * 0.12);
            int ox2 = (w - d) / 2 + (int) (d * 0.22);
            int oy2 = (h - d) / 2 + (int) (d * 0.12);

            Area alma1 = new Area(new Ellipse2D.Double(ox1, oy1, d, d));
            Area alma2 = new Area(new Ellipse2D.Double(ox2, oy2, d, d));
            Area interseccion = new Area(alma1);
            interseccion.intersect(alma2);

            g2.setColor(new Color(Tema.ACENTO.getRed(), Tema.ACENTO.getGreen(), Tema.ACENTO.getBlue(), 70));
            g2.fill(interseccion);

            g2.setStroke(new BasicStroke(2.4f));
            g2.setColor(Tema.ACENTO_FUERTE);
            g2.draw(alma1);
            g2.setColor(Tema.ACENTO);
            g2.draw(alma2);

            g2.dispose();
        }
    }

    // -------------------------------------------------------------- Avatar

    /** Avatar deterministico derivado del UUID offline: un "identicon" propio. */
    private static final class Avatar extends JComponent {
        private static final int TAMANO = 56;
        private static final int CELDAS = 5;

        private boolean[][] celdas = new boolean[CELDAS][CELDAS];
        private Color color = Tema.ACENTO;
        private String uuidTexto = "";

        Avatar() {
            setPreferredSize(new Dimension(TAMANO, TAMANO));
            setMaximumSize(new Dimension(TAMANO, TAMANO));
            setMinimumSize(new Dimension(TAMANO, TAMANO));
            setOpaque(false);
            setToolTipText("Avatar generado a partir del UUID offline");
            actualizar("?");
        }

        void actualizar(String nombre) {
            UUID uuid = MinecraftLauncher.offlineUuid(nombre);
            uuidTexto = uuid.toString();
            long semilla = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
            Random r = new Random(semilla);

            // Patron simetrico (solo se sortea la mitad izquierda + columna
            // central, y se refleja) para que parezca un dibujo, no ruido.
            int mitad = (CELDAS + 1) / 2;
            for (int fila = 0; fila < CELDAS; fila++) {
                for (int col = 0; col < mitad; col++) {
                    boolean valor = r.nextFloat() < 0.55f;
                    celdas[fila][col] = valor;
                    celdas[fila][CELDAS - 1 - col] = valor;
                }
            }

            float hue = (Math.abs(semilla) % 360) / 360f;
            color = Color.getHSBColor(hue, 0.42f, 0.68f);
            setToolTipText("UUID offline: " + uuidTexto);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(Tema.SUPERFICIE_ELEVADA);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));

            int margen = 8;
            int lado = Math.min(w, h) - margen * 2;
            int tamCelda = lado / CELDAS;
            int offX = (w - tamCelda * CELDAS) / 2;
            int offY = (h - tamCelda * CELDAS) / 2;

            g2.setColor(color);
            for (int fila = 0; fila < CELDAS; fila++) {
                for (int col = 0; col < CELDAS; col++) {
                    if (celdas[fila][col]) {
                        g2.fillRoundRect(offX + col * tamCelda + 1, offY + fila * tamCelda + 1,
                                tamCelda - 2, tamCelda - 2, 3, 3);
                    }
                }
            }

            g2.setColor(Tema.BORDE_SUAVE);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, Tema.RADIO_PEQUENO, Tema.RADIO_PEQUENO));
            g2.dispose();
        }
    }
}
