package com.dosalmas.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Panel de ajustes: memoria, rutas de la instalacion, Java, argumentos
 * avanzados y acciones utiles. Sustituye al antiguo SettingsDialog modal: aqui
 * vive incrustado dentro del CardLayout de la ventana.
 *
 * Los campos de ruta se editan libremente y solo se validan al pulsar
 * "Guardar cambios"; el indicador de "existe / no existe" de cada campo, en
 * cambio, se recalcula en vivo pero SIEMPRE en un hilo aparte (comprobar un
 * disco de red no puede congelar la interfaz).
 */
public final class PanelAjustes extends JPanel {

    private final Config config;
    private final Runnable alGuardarConfig;
    private final Runnable alCambiarModo;

    // ------------------------------------------------------------- Memoria
    private DeslizadorMemoria deslizadorMax;
    private JLabel etiquetaPerfilActual;
    private JLabel etiquetaMemoriaMax;
    private DeslizadorMemoria deslizadorMin;
    private JLabel etiquetaMemoriaMin;

    // -------------------------------------------------------------- Rutas
    private Tema.CampoTexto campoGameDir;
    private Tema.CampoTexto campoAssets;
    private Tema.CampoTexto campoJavaBin;
    private Tema.Insignia insigniaGameDir;
    private Tema.Insignia insigniaAssets;
    private Tema.Insignia insigniaJavaBin;
    private Tema.Insignia insigniaJavaVersion;
    private JComboBox<String> comboJavaDetectado;
    private boolean rellenandoCombo = false;
    private boolean cargando = false;

    // ----------------------------------------------------------- Avanzado
    private Tema.CampoTexto campoJvmArgs;
    private JCheckBox checkModoAvanzado;
    private Tema.CampoTexto campoAncho;
    private Tema.CampoTexto campoAlto;

    // ------------------------------------------------------------- Estado
    private JLabel etiquetaEstado;
    private final Timer temporizadorValidacion;

    public PanelAjustes(Config config, Runnable alGuardarConfig, Runnable alCambiarModo) {
        this.config = config;
        this.alGuardarConfig = alGuardarConfig;
        this.alCambiarModo = alCambiarModo;

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Tema.FONDO);
        setBorder(new EmptyBorder(Tema.ESPACIO_GRANDE, Tema.ESPACIO_GRANDE,
                Tema.ESPACIO_PEQUENO, Tema.ESPACIO_GRANDE));

        // Debounce de 350ms: mientras se escribe una ruta no se comprueba en
        // cada tecla, solo cuando la escritura se detiene un instante.
        temporizadorValidacion = new Timer(350, e -> validarRutasEnSegundoPlano());
        temporizadorValidacion.setRepeats(false);

        add(construirCabecera(), BorderLayout.NORTH);

        PanelDesplazable columna = new PanelDesplazable();
        columna.add(construirTarjetaPerfiles());
        columna.add(espacio());
        columna.add(construirTarjetaMemoria());
        columna.add(espacio());
        columna.add(construirTarjetaRutas());
        columna.add(espacio());
        columna.add(construirTarjetaJava());
        columna.add(espacio());
        columna.add(construirTarjetaAvanzado());
        columna.add(espacio());
        columna.add(construirTarjetaAcciones());
        columna.add(Box.createVerticalStrut(Tema.ESPACIO_GRANDE));

        JScrollPane scroll = new JScrollPane(columna,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        add(construirBarraInferior(), BorderLayout.SOUTH);

        cargarDesdeConfig();
    }

    private static Component espacio() {
        return Box.createVerticalStrut(Tema.ESPACIO_MEDIO);
    }

    // ------------------------------------------------------------ Cabecera

    private JComponent construirCabecera() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 0, Tema.ESPACIO_MEDIO, 0));

        JLabel titulo = new JLabel("Ajustes");
        titulo.setFont(Tema.titulo());
        titulo.setForeground(Tema.TEXTO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitulo = new JLabel("Memoria, rutas de la instalacion, Java y modo de la ventana");
        subtitulo.setFont(Tema.cuerpo());
        subtitulo.setForeground(Tema.TEXTO_SUAVE);
        subtitulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titulo);
        panel.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        panel.add(subtitulo);
        return panel;
    }

    // ------------------------------------------------------------- Memoria

    /**
     * Perfil de rendimiento. Ajusta de una vez las opciones del juego, Distant
     * Horizons y Embeddium, que es lo que de verdad separa a una torre con
     * tarjeta dedicada de un portatil con grafica integrada.
     */
    private Tema.Tarjeta construirTarjetaPerfiles() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        Tema.Separador separador = new Tema.Separador("Rendimiento");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        PerfilesServicio.Perfil sugerido = PerfilesServicio.sugerido();
        PerfilesServicio.Perfil puesto = PerfilesServicio.actual(
                config.gameDir != null ? java.nio.file.Paths.get(config.gameDir) : null);

        // Texto que reflowa: con un JLabel <html> la frase se corta a mitad de
        // palabra cuando la ventana baja al minimo (900x600) y no se ve que
        // falta texto.
        Tema.TextoEnvolvente explicacion = new Tema.TextoEnvolvente(
                "Ajusta de una vez la distancia de vision, las sombras, las particulas y el "
                + "horizonte lejano. Para este equipo se recomienda: " + sugerido.nombre + ".");
        explicacion.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(explicacion);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        etiquetaPerfilActual = new JLabel(puesto != null
                ? "Perfil actual: " + puesto.nombre
                : "Perfil actual: ajustes personalizados");
        etiquetaPerfilActual.setFont(Tema.cuerpoNegrita());
        etiquetaPerfilActual.setForeground(Tema.TEXTO);
        etiquetaPerfilActual.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(etiquetaPerfilActual);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));

        // Un boton por perfil, cada uno con su explicacion debajo.
        for (PerfilesServicio.Perfil p : PerfilesServicio.Perfil.values()) {
            // Sin alto maximo fijo: la descripcion puede necesitar dos lineas
            // en una ventana estrecha y la fila tiene que poder crecer.
            JPanel fila = new JPanel(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
            fila.setOpaque(false);
            fila.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel texto = new JPanel();
            texto.setOpaque(false);
            texto.setLayout(new BoxLayout(texto, BoxLayout.Y_AXIS));
            JLabel nombre = new JLabel(p.nombre + (p == sugerido ? "   (recomendado aqui)" : ""));
            nombre.setFont(Tema.cuerpoNegrita());
            nombre.setForeground(p == sugerido ? Tema.ACENTO : Tema.TEXTO);
            nombre.setAlignmentX(Component.LEFT_ALIGNMENT);
            Tema.TextoEnvolvente desc = new Tema.TextoEnvolvente(p.descripcion);
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            texto.add(nombre);
            texto.add(desc);
            fila.add(texto, BorderLayout.CENTER);

            Tema.BotonSecundario aplicar = new Tema.BotonSecundario("Aplicar");
            aplicar.addActionListener(e -> aplicarPerfil(p));
            JPanel derecha = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            derecha.setOpaque(false);
            derecha.add(aplicar);
            fila.add(derecha, BorderLayout.EAST);

            cuerpo.add(fila);
            cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_PEQUENO));
        }

        Tema.TextoEnvolvente nota = new Tema.TextoEnvolvente(
                "Se guarda una copia de cada archivo antes de cambiarlo (.antes-de-perfiles). "
                + "No se activan ni desactivan mods: eso se hace en la seccion Mods.");
        nota.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(nota);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    /** Aplica un perfil y cuenta al usuario exactamente que cambio. */
    private void aplicarPerfil(PerfilesServicio.Perfil p) {
        java.nio.file.Path dir = (config.gameDir != null)
                ? java.nio.file.Paths.get(config.gameDir) : null;
        if (dir == null) {
            JOptionPane.showMessageDialog(this,
                    "Primero indica la carpeta del juego.", "Falta la instalacion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "Se va a aplicar el perfil \"" + p.nombre + "\".\n\n"
                        + p.descripcion + "\n\n"
                        + "Se cambiaran las opciones del juego, Distant Horizons y Embeddium.\n"
                        + "Si el juego esta abierto, los cambios se veran al reiniciarlo.\n\n"
                        + "¿Continuar?",
                "Aplicar perfil de rendimiento", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        PerfilesServicio.Resultado res = PerfilesServicio.aplicar(dir, p);

        // La memoria del perfil se propone, pero no se impone en silencio.
        int memoria = p.memoriaMb(Config.ramTotalMb());
        StringBuilder sb = new StringBuilder();
        for (String a : res.aplicados) sb.append("  - ").append(a).append('\n');
        for (String a : res.avisos)    sb.append("  ! ").append(a).append('\n');

        if (res.correcto()) {
            int m = JOptionPane.showConfirmDialog(this,
                    "Perfil \"" + p.nombre + "\" aplicado:\n\n" + sb
                            + "\n¿Ajustar tambien la memoria a " + (memoria / 1024) + " GB?",
                    "Perfil aplicado", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (m == JOptionPane.YES_OPTION) {
                config.maxMemoryMb = memoria;
                try {
                    config.save();
                    cargarDesdeConfig();
                    if (alGuardarConfig != null) alGuardarConfig.run();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                            "El perfil se aplico, pero no se pudo guardar la memoria:\n"
                                    + Mensajes.explicar(ex),
                            "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }
            etiquetaPerfilActual.setText("Perfil actual: " + p.nombre);
        } else {
            JOptionPane.showMessageDialog(this,
                    "El perfil se aplico solo en parte:\n\n" + sb,
                    "Perfil aplicado con avisos", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Tema.Tarjeta construirTarjetaMemoria() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        long totalMb = Config.ramTotalMb();
        int totalGb = (int) (totalMb / 1024);
        // Se reservan 2 GB para el sistema operativo: si el deslizador permite
        // pedir toda la RAM del equipo, el sistema mata al juego o se cuelga.
        // Ademas se limita a 32 GB: en un equipo con mucha RAM (la torre tiene
        // 125 GB), un techo de 123 GB deja el rango realmente util (4-16 GB)
        // aplastado en el 10% izquierdo de la barra, con el tirador y el punto
        // verde recomendado pegados uno al otro. Mas alla de 32 GB no hay
        // ninguna ganancia real para este modpack.
        int techoMb = (int) Math.min(Math.max(2048, totalMb - 2048), 32768);
        int recomendadoMb = Config.memoriaRecomendadaMb();

        Tema.Separador separador = new Tema.Separador("Memoria");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        JLabel explicacion = new JLabel("<html>Mas memoria no siempre es mejor: dar toda la RAM del "
                + "equipo al juego no lo hace mas rapido y puede ralentizar el resto del sistema.</html>");
        explicacion.setFont(Tema.etiqueta());
        explicacion.setForeground(Tema.TEXTO_SUAVE);
        explicacion.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(explicacion);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        etiquetaMemoriaMax = new JLabel();
        etiquetaMemoriaMax.setFont(Tema.cuerpoNegrita());
        etiquetaMemoriaMax.setForeground(Tema.TEXTO);
        etiquetaMemoriaMax.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(etiquetaMemoriaMax);

        deslizadorMax = new DeslizadorMemoria(2048, techoMb, config.maxMemoryMb, recomendadoMb);
        deslizadorMax.setAlignmentX(Component.LEFT_ALIGNMENT);
        deslizadorMax.setAlCambiar(v -> {
            etiquetaMemoriaMax.setText(rotuloMemoria(v, totalGb));
            marcarSinGuardar();
            // La memoria minima nunca puede superar a la maxima.
            if (deslizadorMin != null && deslizadorMin.getValorMb() > v) {
                deslizadorMin.setValorMb(v);
                etiquetaMemoriaMin.setText(rotuloMemoriaMin(v));
            }
        });
        cuerpo.add(deslizadorMax);
        etiquetaMemoriaMax.setText(rotuloMemoria(config.maxMemoryMb, totalGb));

        JLabel notaRecomendado = new JLabel("El punto verde marca el valor recomendado para este equipo ("
                + (recomendadoMb / 1024) + " GB).");
        notaRecomendado.setFont(Tema.etiqueta());
        notaRecomendado.setForeground(Tema.TEXTO_DEBIL);
        notaRecomendado.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(notaRecomendado);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        etiquetaMemoriaMin = new JLabel();
        etiquetaMemoriaMin.setFont(Tema.cuerpoNegrita());
        etiquetaMemoriaMin.setForeground(Tema.TEXTO);
        etiquetaMemoriaMin.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(etiquetaMemoriaMin);

        deslizadorMin = new DeslizadorMemoria(512, Math.max(1024, config.maxMemoryMb), config.minMemoryMb, -1);
        deslizadorMin.setAlignmentX(Component.LEFT_ALIGNMENT);
        deslizadorMin.setAlCambiar(v -> {
            etiquetaMemoriaMin.setText(rotuloMemoriaMin(v));
            marcarSinGuardar();
        });
        cuerpo.add(deslizadorMin);
        etiquetaMemoriaMin.setText(rotuloMemoriaMin(config.minMemoryMb));

        JLabel notaMin = new JLabel("Memoria minima al arrancar el juego (rara vez hace falta tocarla).");
        notaMin.setFont(Tema.etiqueta());
        notaMin.setForeground(Tema.TEXTO_DEBIL);
        notaMin.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(notaMin);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    private String rotuloMemoria(int valorMb, int totalGb) {
        return String.format("Memoria maxima: %.1f GB de %d GB", valorMb / 1024.0, totalGb);
    }

    private String rotuloMemoriaMin(int valorMb) {
        return String.format("Memoria minima: %.1f GB", valorMb / 1024.0);
    }

    // -------------------------------------------------------------- Rutas

    private Tema.Tarjeta construirTarjetaRutas() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        Tema.Separador separador = new Tema.Separador("Rutas");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        campoGameDir = new Tema.CampoTexto();
        campoAssets = new Tema.CampoTexto();
        campoJavaBin = new Tema.CampoTexto();
        insigniaGameDir = new Tema.Insignia("comprobando...", Tema.Estado.NEUTRO);
        insigniaAssets = new Tema.Insignia("comprobando...", Tema.Estado.NEUTRO);
        insigniaJavaBin = new Tema.Insignia("comprobando...", Tema.Estado.NEUTRO);

        cuerpo.add(construirFilaRuta("Carpeta del juego", campoGameDir, insigniaGameDir, true));
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
        cuerpo.add(construirFilaRuta("Carpeta de assets", campoAssets, insigniaAssets, true));
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));
        cuerpo.add(construirFilaRuta("Binario de Java", campoJavaBin, insigniaJavaBin, false));
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        Tema.BotonSecundario botonDetectar = new Tema.BotonSecundario("Detectar automaticamente");
        botonDetectar.setAlignmentX(Component.LEFT_ALIGNMENT);
        botonDetectar.addActionListener(e -> detectarAutomaticamente());
        cuerpo.add(botonDetectar);

        JLabel nota = new JLabel("Util al llevar el launcher a otro equipo: busca el pack, los assets y un Java valido.");
        nota.setFont(Tema.etiqueta());
        nota.setForeground(Tema.TEXTO_DEBIL);
        nota.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        cuerpo.add(nota);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    private JComponent construirFilaRuta(String etiquetaTexto, Tema.CampoTexto campo,
                                          Tema.Insignia insignia, boolean carpeta) {
        JPanel fila = new JPanel();
        fila.setOpaque(false);
        fila.setLayout(new BoxLayout(fila, BoxLayout.Y_AXIS));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filaEtiqueta = new JPanel(new BorderLayout());
        filaEtiqueta.setOpaque(false);
        JLabel lbl = new JLabel(etiquetaTexto);
        lbl.setFont(Tema.etiqueta());
        lbl.setForeground(Tema.TEXTO_SUAVE);
        filaEtiqueta.add(lbl, BorderLayout.WEST);
        filaEtiqueta.add(insignia, BorderLayout.EAST);
        filaEtiqueta.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaEtiqueta.setMaximumSize(new Dimension(Integer.MAX_VALUE, insignia.getPreferredSize().height + 4));

        JPanel filaCampo = new JPanel(new BorderLayout(Tema.ESPACIO_PEQUENO, 0));
        filaCampo.setOpaque(false);
        filaCampo.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaCampo.setMaximumSize(new Dimension(Integer.MAX_VALUE, campo.getPreferredSize().height + 10));
        Tema.BotonSecundario examinar = new Tema.BotonSecundario("Examinar...");
        examinar.addActionListener(e -> examinarRuta(campo, carpeta));
        filaCampo.add(campo, BorderLayout.CENTER);
        filaCampo.add(examinar, BorderLayout.EAST);

        campo.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { reprogramarValidacion(); }
            @Override public void removeUpdate(DocumentEvent e) { reprogramarValidacion(); }
            @Override public void changedUpdate(DocumentEvent e) { reprogramarValidacion(); }
        });

        fila.add(filaEtiqueta);
        fila.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        fila.add(filaCampo);
        return fila;
    }

    private void examinarRuta(Tema.CampoTexto campo, boolean carpeta) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(carpeta ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        String actual = campo.getText().trim();
        if (!actual.isEmpty()) {
            File f = new File(actual);
            if (f.exists()) chooser.setCurrentDirectory(carpeta ? f : f.getParentFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            campo.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void reprogramarValidacion() {
        marcarSinGuardar();
        temporizadorValidacion.restart();
    }

    /** Comprueba en un hilo aparte si las rutas escritas existen; nunca en el EDT. */
    private void validarRutasEnSegundoPlano() {
        String gameDir = campoGameDir.getText().trim();
        String assets = campoAssets.getText().trim();
        String javaBin = campoJavaBin.getText().trim();

        Thread hilo = new Thread(() -> {
            boolean okGame = !gameDir.isEmpty() && Files.isDirectory(Paths.get(gameDir));
            boolean okAssets = !assets.isEmpty() && Files.isDirectory(Paths.get(assets));
            boolean okJava = !javaBin.isEmpty() && Files.isExecutable(Paths.get(javaBin))
                    && !Files.isDirectory(Paths.get(javaBin));
            int mayor = okJava ? Rutas.versionMayorDe(Paths.get(javaBin)) : -1;

            SwingUtilities.invokeLater(() -> {
                actualizarInsignia(insigniaGameDir, okGame, "Carpeta encontrada", "No existe esa carpeta");
                actualizarInsignia(insigniaAssets, okAssets, "Carpeta encontrada", "No existe esa carpeta");
                actualizarInsignia(insigniaJavaBin, okJava, "Ejecutable encontrado", "No es un ejecutable valido");
                actualizarInsigniaJava(mayor);
            });
        }, "validar-rutas-ajustes");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void actualizarInsignia(Tema.Insignia insignia, boolean ok, String textoOk, String textoMal) {
        insignia.setTexto(ok ? textoOk : textoMal);
        insignia.setEstado(ok ? Tema.Estado.EXITO : Tema.Estado.PELIGRO);
    }

    private void actualizarInsigniaJava(int mayor) {
        if (insigniaJavaVersion == null) return;
        if (mayor <= 0) {
            insigniaJavaVersion.setTexto("Version no detectada");
            insigniaJavaVersion.setEstado(Tema.Estado.NEUTRO);
        } else if (mayor == 17) {
            insigniaJavaVersion.setTexto("Java " + mayor + " (correcto)");
            insigniaJavaVersion.setEstado(Tema.Estado.EXITO);
        } else {
            insigniaJavaVersion.setTexto("Java " + mayor + " -- el pack pide Java 17");
            insigniaJavaVersion.setEstado(Tema.Estado.PELIGRO);
        }
    }

    private void detectarAutomaticamente() {
        etiquetaEstado.setText("Detectando...");
        etiquetaEstado.setForeground(Tema.TEXTO_SUAVE);
        Thread hilo = new Thread(() -> {
            Path dir = Rutas.detectarGameDir();
            String versionId = Rutas.detectarVersionId(dir);
            if (versionId == null) versionId = Rutas.VERSION_POR_DEFECTO;
            Path assets = Rutas.detectarAssets(dir, versionId);
            Path java = Rutas.detectarJava(dir, 17);

            SwingUtilities.invokeLater(() -> {
                if (dir != null) campoGameDir.setText(dir.toString());
                if (assets != null) campoAssets.setText(assets.toString());
                if (java != null) campoJavaBin.setText(java.toString());
                rellenarComboJava(dir);
                if (dir == null) {
                    etiquetaEstado.setText("No se encontro el pack automaticamente; escribe la ruta a mano.");
                    etiquetaEstado.setForeground(Tema.AVISO);
                } else {
                    etiquetaEstado.setText("Deteccion completada. Revisa los campos y pulsa \"Guardar cambios\".");
                    etiquetaEstado.setForeground(Tema.TEXTO_SUAVE);
                }
                reprogramarValidacion();
            });
        }, "deteccion-automatica-ajustes");
        hilo.setDaemon(true);
        hilo.start();
    }

    // --------------------------------------------------------------- Java

    private Tema.Tarjeta construirTarjetaJava() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        Tema.Separador separador = new Tema.Separador("Java");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        JPanel filaEstado = new JPanel(new BorderLayout());
        filaEstado.setOpaque(false);
        filaEstado.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaEstado.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lbl = new JLabel("Version detectada del binario de Java de arriba");
        lbl.setFont(Tema.etiqueta());
        lbl.setForeground(Tema.TEXTO_SUAVE);
        insigniaJavaVersion = new Tema.Insignia("comprobando...", Tema.Estado.NEUTRO);
        filaEstado.add(lbl, BorderLayout.WEST);
        filaEstado.add(insigniaJavaVersion, BorderLayout.EAST);
        cuerpo.add(filaEstado);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        JLabel lblCombo = new JLabel("Instalaciones de Java encontradas en este equipo");
        lblCombo.setFont(Tema.etiqueta());
        lblCombo.setForeground(Tema.TEXTO_SUAVE);
        lblCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(lblCombo);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_XS));

        comboJavaDetectado = new JComboBox<>();
        comboJavaDetectado.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboJavaDetectado.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        comboJavaDetectado.setFont(Tema.mono());
        // Era el unico control sin tematizar: Metal le pone un borde
        // rectangular claro y un boton de flecha con su propio fondo, sin
        // flecha visible sobre nuestro tema oscuro. setUI() primero, y el
        // borde y los colores DESPUES (mismo orden que exige el JSplitPane
        // de PanelRegistros: si el borde se pone antes, installDefaults lo
        // vuelve a pisar).
        comboJavaDetectado.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton boton = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        Tema.antialias(g2);
                        g2.setColor(Tema.SUPERFICIE_ELEVADA);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        g2.setColor(Tema.TEXTO_SUAVE);
                        int[] xs = {cx - 4, cx + 4, cx};
                        int[] ys = {cy - 2, cy - 2, cy + 4};
                        g2.fillPolygon(xs, ys, 3);
                        g2.dispose();
                    }
                };
                boton.setOpaque(false);
                boton.setBorder(null);
                boton.setContentAreaFilled(false);
                boton.setFocusPainted(false);
                return boton;
            }
        });
        comboJavaDetectado.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        comboJavaDetectado.setBackground(Tema.SUPERFICIE_ELEVADA);
        comboJavaDetectado.setForeground(Tema.TEXTO);
        comboJavaDetectado.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                       boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setOpaque(true);
                l.setBackground(isSelected ? Tema.ACENTO_APAGADO : Tema.SUPERFICIE);
                l.setForeground(Tema.TEXTO);
                l.setFont(Tema.mono());
                return l;
            }
        });
        comboJavaDetectado.addActionListener(e -> {
            if (rellenandoCombo) return;
            Object sel = comboJavaDetectado.getSelectedItem();
            if (sel != null) {
                campoJavaBin.setText(sel.toString());
            }
        });
        cuerpo.add(comboJavaDetectado);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    private void rellenarComboJava(Path gameDir) {
        Thread hilo = new Thread(() -> {
            List<Path> candidatos = Rutas.candidatosJava(gameDir);
            String[] validos = candidatos.stream()
                    .filter(Files::isExecutable)
                    .map(Path::toString)
                    .distinct()
                    .toArray(String[]::new);
            SwingUtilities.invokeLater(() -> {
                rellenandoCombo = true;
                comboJavaDetectado.removeAllItems();
                for (String v : validos) comboJavaDetectado.addItem(v);
                rellenandoCombo = false;
            });
        }, "candidatos-java-ajustes");
        hilo.setDaemon(true);
        hilo.start();
    }

    // ---------------------------------------------------------- Avanzado

    private Tema.Tarjeta construirTarjetaAvanzado() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        Tema.Separador separador = new Tema.Separador("Avanzado");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        checkModoAvanzado = new JCheckBox("Modo avanzado (consola en vivo y todas las secciones)");
        checkModoAvanzado.setOpaque(false);
        checkModoAvanzado.setForeground(Tema.TEXTO);
        checkModoAvanzado.setFont(Tema.cuerpo());
        checkModoAvanzado.setFocusPainted(false);
        checkModoAvanzado.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkModoAvanzado.addActionListener(e -> cambiarModoAvanzado(checkModoAvanzado.isSelected()));
        cuerpo.add(checkModoAvanzado);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        JLabel lblJvm = new JLabel("Argumentos JVM adicionales");
        lblJvm.setFont(Tema.etiqueta());
        lblJvm.setForeground(Tema.TEXTO_SUAVE);
        lblJvm.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(lblJvm);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_XS));

        campoJvmArgs = new Tema.CampoTexto();
        campoJvmArgs.setSugerencia("por ejemplo: -Dfile.encoding=UTF-8");
        campoJvmArgs.setAlignmentX(Component.LEFT_ALIGNMENT);
        campoJvmArgs.setMaximumSize(new Dimension(Integer.MAX_VALUE, campoJvmArgs.getPreferredSize().height + 10));
        campoJvmArgs.getDocument().addDocumentListener(soloMarcarCambios());
        cuerpo.add(campoJvmArgs);

        JLabel avisoJvm = new JLabel("Cuidado: un argumento incorrecto puede impedir que el juego arranque.");
        avisoJvm.setFont(Tema.etiqueta());
        avisoJvm.setForeground(Tema.AVISO);
        avisoJvm.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_XS));
        cuerpo.add(avisoJvm);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_MEDIO));

        JLabel lblResolucion = new JLabel("Resolucion de ventana (vacio = automatica)");
        lblResolucion.setFont(Tema.etiqueta());
        lblResolucion.setForeground(Tema.TEXTO_SUAVE);
        lblResolucion.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(lblResolucion);
        cuerpo.add(Box.createVerticalStrut(Tema.ESPACIO_XS));

        JPanel filaResolucion = new JPanel(new FlowLayout(FlowLayout.LEFT, Tema.ESPACIO_PEQUENO, 0));
        filaResolucion.setOpaque(false);
        filaResolucion.setAlignmentX(Component.LEFT_ALIGNMENT);
        campoAncho = new Tema.CampoTexto(6);
        campoAncho.setSugerencia("ancho");
        campoAlto = new Tema.CampoTexto(6);
        campoAlto.setSugerencia("alto");
        campoAncho.getDocument().addDocumentListener(soloMarcarCambios());
        campoAlto.getDocument().addDocumentListener(soloMarcarCambios());
        filaResolucion.add(campoAncho);
        JLabel por = new JLabel("x");
        por.setForeground(Tema.TEXTO_SUAVE);
        filaResolucion.add(por);
        filaResolucion.add(campoAlto);
        cuerpo.add(filaResolucion);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    private DocumentListener soloMarcarCambios() {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { marcarSinGuardar(); }
            @Override public void removeUpdate(DocumentEvent e) { marcarSinGuardar(); }
            @Override public void changedUpdate(DocumentEvent e) { marcarSinGuardar(); }
        };
    }

    private void cambiarModoAvanzado(boolean activo) {
        config.advancedMode = activo;
        try {
            config.save();
        } catch (IOException ex) {
            // El modo ya cambio en memoria; si falla el guardado se avisa
            // pero no tiene sentido revertir el interruptor bajo el dedo del
            // usuario.
            etiquetaEstado.setText("El modo cambio, pero no se pudo guardar: " + Mensajes.explicar(ex));
            etiquetaEstado.setForeground(Tema.PELIGRO);
        }
        if (alCambiarModo != null) alCambiarModo.run();
    }

    // ----------------------------------------------------------- Acciones

    private Tema.Tarjeta construirTarjetaAcciones() {
        Tema.Tarjeta tarjeta = new Tema.Tarjeta(new BorderLayout(0, Tema.ESPACIO_MEDIO));

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        Tema.Separador separador = new Tema.Separador("Acciones");
        separador.setAlignmentX(Component.LEFT_ALIGNMENT);
        cuerpo.add(separador);

        JPanel filaBotones = new FilaFluida(Tema.ESPACIO_PEQUENO, Tema.ESPACIO_XS);
        filaBotones.setAlignmentX(Component.LEFT_ALIGNMENT);

        Tema.BotonSecundario abrirJuego = new Tema.BotonSecundario("Abrir carpeta del juego");
        abrirJuego.addActionListener(e -> abrirCarpeta(campoGameDir.getText().trim()));
        Tema.BotonSecundario abrirLogs = new Tema.BotonSecundario("Abrir carpeta de registros");
        abrirLogs.addActionListener(e -> abrirCarpeta(Rutas.carpetaLogs().toString()));
        Tema.BotonSecundario restablecer = new Tema.BotonSecundario("Restablecer valores");
        restablecer.addActionListener(e -> restablecerValores());

        filaBotones.add(abrirJuego);
        filaBotones.add(abrirLogs);
        filaBotones.add(restablecer);
        cuerpo.add(filaBotones);

        tarjeta.add(cuerpo, BorderLayout.CENTER);
        return tarjeta;
    }

    private void abrirCarpeta(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            etiquetaEstado.setText("No hay ninguna ruta que abrir todavia.");
            etiquetaEstado.setForeground(Tema.AVISO);
            return;
        }
        File carpeta = new File(ruta);
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            etiquetaEstado.setText("Este sistema no permite abrir carpetas desde el launcher. Ruta: " + ruta);
            etiquetaEstado.setForeground(Tema.AVISO);
            return;
        }
        try {
            if (!carpeta.exists()) Files.createDirectories(carpeta.toPath());
            Desktop.getDesktop().open(carpeta);
        } catch (Exception ex) {
            etiquetaEstado.setText("No se pudo abrir la carpeta: " + Mensajes.explicar(ex));
            etiquetaEstado.setForeground(Tema.PELIGRO);
        }
    }

    private void restablecerValores() {
        Config limpio = new Config();
        limpio.completarConDeteccion();
        config.gameDir = limpio.gameDir;
        config.versionId = limpio.versionId;
        config.assetsRoot = limpio.assetsRoot;
        config.javaBin = limpio.javaBin;
        config.maxMemoryMb = limpio.maxMemoryMb;
        config.minMemoryMb = limpio.minMemoryMb;
        config.extraJvmArgs = "";
        config.width = null;
        config.height = null;
        cargarDesdeConfig();
        etiquetaEstado.setText("Valores restablecidos a la deteccion automatica. Pulsa \"Guardar cambios\" para conservarlos.");
        etiquetaEstado.setForeground(Tema.TEXTO_SUAVE);
    }

    // -------------------------------------------------------- Barra inferior

    private JComponent construirBarraInferior() {
        JPanel barra = new JPanel(new BorderLayout(Tema.ESPACIO_MEDIO, 0));
        barra.setOpaque(false);
        barra.setBorder(new EmptyBorder(Tema.ESPACIO_MEDIO, 0, Tema.ESPACIO_PEQUENO, 0));

        etiquetaEstado = new JLabel(" ");
        etiquetaEstado.setFont(Tema.etiqueta());
        etiquetaEstado.setForeground(Tema.TEXTO_SUAVE);
        barra.add(etiquetaEstado, BorderLayout.CENTER);

        Tema.BotonPrimario guardar = new Tema.BotonPrimario("Guardar cambios");
        guardar.addActionListener(e -> guardarCambios());
        barra.add(guardar, BorderLayout.EAST);
        return barra;
    }

    private void marcarSinGuardar() {
        // Al rellenar los campos desde la configuracion se disparan los mismos
        // avisos de cambio que al escribir, y el panel decia "hay cambios sin
        // guardar" nada mas abrirlo, sin que nadie hubiera tocado nada.
        if (etiquetaEstado == null || cargando) return;
        etiquetaEstado.setText("Hay cambios sin guardar.");
        etiquetaEstado.setForeground(Tema.AVISO);
    }

    // -------------------------------------------------------- Guardar/cargar

    private void cargarDesdeConfig() {
        cargando = true;
        try {
        campoGameDir.setText(config.gameDir != null ? config.gameDir : "");
        campoAssets.setText(config.assetsRoot != null ? config.assetsRoot : "");
        campoJavaBin.setText(config.javaBin != null ? config.javaBin : "");
        campoJvmArgs.setText(config.extraJvmArgs != null ? config.extraJvmArgs : "");
        campoAncho.setText(config.width != null ? String.valueOf(config.width) : "");
        campoAlto.setText(config.height != null ? String.valueOf(config.height) : "");
        checkModoAvanzado.setSelected(config.advancedMode);

        long totalMb = Config.ramTotalMb();
        int totalGb = (int) (totalMb / 1024);
        deslizadorMax.setValorMb(config.maxMemoryMb);
        etiquetaMemoriaMax.setText(rotuloMemoria(config.maxMemoryMb, totalGb));
        deslizadorMin.setValorMb(config.minMemoryMb);
        etiquetaMemoriaMin.setText(rotuloMemoriaMin(config.minMemoryMb));

        Path dir = (config.gameDir != null && !config.gameDir.isBlank()) ? Paths.get(config.gameDir) : null;
        rellenarComboJava(dir);
        reprogramarValidacion();
            } finally {
            cargando = false;
        }
}

    /** Recarga los campos desde la configuracion actual (por si otro panel la cambio). */
    public void refrescar() {
        cargarDesdeConfig();
        etiquetaEstado.setText(" ");
        etiquetaEstado.setForeground(Tema.TEXTO_SUAVE);
    }

    private void guardarCambios() {
        String gameDir = campoGameDir.getText().trim();
        String assets = campoAssets.getText().trim();
        String javaBin = campoJavaBin.getText().trim();

        campoGameDir.setError(false);
        campoAssets.setError(false);
        campoJavaBin.setError(false);
        campoAncho.setError(false);
        campoAlto.setError(false);

        if (!gameDir.isEmpty() && !Files.isDirectory(Paths.get(gameDir))) {
            campoGameDir.setError(true);
            mostrarErrorValidacion("La carpeta del juego no existe. Corrigela o usa \"Detectar automaticamente\".");
            return;
        }
        if (!assets.isEmpty() && !Files.isDirectory(Paths.get(assets))) {
            campoAssets.setError(true);
            mostrarErrorValidacion("La carpeta de assets no existe.");
            return;
        }
        if (!javaBin.isEmpty()) {
            Path p = Paths.get(javaBin);
            if (!Files.isExecutable(p) || Files.isDirectory(p)) {
                campoJavaBin.setError(true);
                mostrarErrorValidacion("El binario de Java indicado no existe o no es ejecutable.");
                return;
            }
        }

        Integer ancho = analizarEntero(campoAncho.getText().trim());
        Integer alto = analizarEntero(campoAlto.getText().trim());
        if (campoAncho.getText().trim().isEmpty() != campoAlto.getText().trim().isEmpty()) {
            campoAncho.setError(true);
            campoAlto.setError(true);
            mostrarErrorValidacion("Indica ancho y alto de la ventana los dos, o dejalos los dos en blanco.");
            return;
        }
        if (!campoAncho.getText().trim().isEmpty() && (ancho == null || ancho < 200)) {
            campoAncho.setError(true);
            mostrarErrorValidacion("El ancho de ventana no es un numero valido.");
            return;
        }
        if (!campoAlto.getText().trim().isEmpty() && (alto == null || alto < 200)) {
            campoAlto.setError(true);
            mostrarErrorValidacion("El alto de ventana no es un numero valido.");
            return;
        }

        int maxMb = deslizadorMax.getValorMb();
        int minMb = deslizadorMin.getValorMb();
        if (minMb > maxMb) minMb = maxMb;

        config.gameDir = gameDir.isEmpty() ? null : gameDir;
        config.assetsRoot = assets.isEmpty() ? null : assets;
        config.javaBin = javaBin.isEmpty() ? null : javaBin;
        config.extraJvmArgs = campoJvmArgs.getText().trim();
        config.maxMemoryMb = maxMb;
        config.minMemoryMb = minMb;
        config.width = ancho;
        config.height = alto;
        config.advancedMode = checkModoAvanzado.isSelected();

        // La version instalada puede depender de la nueva carpeta del juego.
        if (config.gameDir != null) {
            String v = Rutas.detectarVersionId(Paths.get(config.gameDir));
            if (v != null) config.versionId = v;
        }

        try {
            config.save();
            etiquetaEstado.setText("Cambios guardados.");
            etiquetaEstado.setForeground(Tema.EXITO);
        } catch (IOException ex) {
            etiquetaEstado.setText("No se pudo guardar: " + Mensajes.explicar(ex));
            etiquetaEstado.setForeground(Tema.PELIGRO);
            return;
        }
        if (alGuardarConfig != null) alGuardarConfig.run();
        reprogramarValidacion();
    }

    private void mostrarErrorValidacion(String texto) {
        etiquetaEstado.setText(texto);
        etiquetaEstado.setForeground(Tema.PELIGRO);
    }

    private static Integer analizarEntero(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Columna vertical de tarjetas para meter dentro de un JScrollPane. Sin
     * esto, un JPanel normal no implementa Scrollable y el viewport le da su
     * ANCHO PREFERIDO en vez del ancho disponible: si algun hijo (el combo de
     * Java, por ejemplo) pide mas ancho del que hay, todo lo que quede a la
     * derecha de esa anchura -- incluidas las insignias de estado -- se
     * recorta en silencio y no hay forma de desplazarse hasta verlo. Al fijar
     * getScrollableTracksViewportWidth() en true, la columna SIEMPRE ocupa el
     * ancho del viewport y solo se desplaza en vertical.
     */
    private static final class PanelDesplazable extends JPanel implements Scrollable {
        PanelDesplazable() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientacion, int direccion) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientacion, int direccion) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /**
     * Fila de botones que envuelve a una segunda linea si no caben, sin el
     * fallo de FlowLayout: FlowLayout.getPreferredSize() siempre calcula como
     * si fuera una unica fila, sin importar el ancho real disponible. Dentro
     * de un BoxLayout vertical eso reserva poca altura y el boton que envolvio
     * queda recortado -- invisible, aunque siga estando ahi y sea pulsable en
     * teoria. Aqui se recalcula la altura mirando el ancho ya asignado al
     * panel padre (el BoxLayout ya lo fijo en el momento en que pide este
     * tamano, por eso funciona sin necesitar una segunda pasada de layout).
     */
    private static final class FilaFluida extends JPanel {
        FilaFluida(int hgap, int vgap) {
            super(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
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

    // =================================================== Deslizador propio

    /**
     * Deslizador de memoria pintado a mano con Java2D: el JSlider de Metal no
     * encaja con el resto del tema y no permite marcar un valor recomendado.
     * Arrastra o pulsa sobre la barra para cambiar el valor; redondea a
     * multiplos de 128 MB para que no queden numeros con decimales raros.
     */
    private static final class DeslizadorMemoria extends JComponent {
        private final int minMb;
        private final int maxMb;
        private int valorMb;
        private final int recomendadoMb; // -1 si no aplica
        private IntConsumer alCambiar;
        private boolean sobreHilo = false;

        DeslizadorMemoria(int minMb, int maxMb, int valorInicial, int recomendadoMb) {
            this.minMb = minMb;
            this.maxMb = Math.max(maxMb, minMb + 256);
            this.recomendadoMb = recomendadoMb;
            this.valorMb = limitar(valorInicial);
            setOpaque(false);
            setPreferredSize(new Dimension(200, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { sobreHilo = true; actualizarDesde(e.getX()); }
                @Override public void mouseDragged(MouseEvent e) { actualizarDesde(e.getX()); }
                @Override public void mouseReleased(MouseEvent e) { sobreHilo = false; repaint(); }
                @Override public void mouseEntered(MouseEvent e) { sobreHilo = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { sobreHilo = false; repaint(); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setAlCambiar(IntConsumer c) { this.alCambiar = c; }

        int getValorMb() { return valorMb; }

        void setValorMb(int v) {
            valorMb = limitar(v);
            repaint();
        }

        private int limitar(int v) { return Math.max(minMb, Math.min(maxMb, v)); }

        private void actualizarDesde(int x) {
            int pad = 9;
            int ancho = Math.max(1, getWidth() - pad * 2);
            double frac = (x - pad) / (double) ancho;
            frac = Math.max(0, Math.min(1, frac));
            int v = minMb + (int) Math.round(frac * (maxMb - minMb));
            v = Math.round(v / 128f) * 128;
            valorMb = limitar(v);
            repaint();
            if (alCambiar != null) alCambiar.accept(valorMb);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Tema.antialias(g2);

            int pad = 9;
            int y = getHeight() / 2;
            int ancho = Math.max(1, getWidth() - pad * 2);

            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Tema.BORDE_SUAVE);
            g2.drawLine(pad, y, pad + ancho, y);

            double fracVal = (valorMb - minMb) / (double) (maxMb - minMb);
            int xVal = pad + (int) Math.round(fracVal * ancho);
            g2.setColor(Tema.ACENTO);
            g2.drawLine(pad, y, xVal, y);

            if (recomendadoMb > minMb) {
                double fracRec = (recomendadoMb - minMb) / (double) (maxMb - minMb);
                int xRec = pad + (int) Math.round(fracRec * ancho);
                g2.setColor(Tema.EXITO);
                g2.fillOval(xRec - 3, y - 9, 6, 6);
            }

            g2.setColor(sobreHilo ? Tema.ACENTO_FUERTE : Tema.ACENTO);
            g2.fillOval(xVal - 8, y - 8, 16, 16);
            g2.setColor(Tema.FONDO_ALTO);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(xVal - 8, y - 8, 16, 16);

            g2.dispose();
        }
    }
}
