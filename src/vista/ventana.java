package vista;

import controlador.logica_ventana;
import modelo.persona;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

/**
 * Vista (GUI) del sistema. Pública para que el controlador pueda acceder a componentes.
 * Incluye campo de búsqueda txtBuscar.
 */
public class ventana extends JFrame {

    // Componentes públicos accesibles desde el controlador
    public JTextField txtNombre, txtTelefono, txtEmail, txtBuscar;
    public JCheckBox chkFavorito;
    public JComboBox<String> cmbCategoria, cmbIdioma;
    public JTable tablaContactos;
    public JButton btnAgregar, btnEliminar, btnExportar, btnEditar;
    public JLabel lblTotal, lblFavoritos, lblCategoria;

    // Mapa de idiomas (expuesto para reutilización en controlador)
    public final Map<String, Map<String, String>> idiomas = new HashMap<>();
    private final JProgressBar progressBar = new JProgressBar();

    public ventana() {
        setTitle("Gestión de Contactos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        inicializarIdiomas();
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Contactos", crearPanelContactos());
        tabs.add("Estadísticas", crearPanelEstadisticas());
        add(tabs, BorderLayout.CENTER);

        // Barra de progreso (simula carga)
        progressBar.setIndeterminate(true);
        add(progressBar, BorderLayout.SOUTH);
        javax.swing.Timer t = new javax.swing.Timer(1200, e -> progressBar.setVisible(false));
        t.setRepeats(false);
        t.start();

        // Inicializar controlador (registro de eventos)
        new logica_ventana(this);

        setVisible(true);
    }

    private JPanel crearPanelContactos() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // PANEL FORMULARIO (izquierda)
        JPanel form = new JPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;

        // TOP: idioma + buscador
        JPanel top = new JPanel(new BorderLayout(8,0));
        JPanel idiomaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel lblIdioma = new JLabel("Idioma:");
        cmbIdioma = new JComboBox<>(new String[]{"Español", "Inglés", "Francés"});
        idiomaPanel.add(lblIdioma); idiomaPanel.add(cmbIdioma);
        top.add(idiomaPanel, BorderLayout.WEST);

        // Campo de búsqueda (nuevo) -- se añade aquí y es público
        JPanel buscadorPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JLabel lblBuscar = new JLabel("Buscar:");
        txtBuscar = new JTextField(18);
        buscadorPanel.add(lblBuscar); buscadorPanel.add(txtBuscar);
        top.add(buscadorPanel, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        // Campos del formulario (en form)
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1;
        txtNombre = new JTextField(18); form.add(txtNombre, gbc);

        gbc.gridy++; gbc.gridx = 0;
        form.add(new JLabel("Teléfono:"), gbc);
        gbc.gridx = 1;
        txtTelefono = new JTextField(18); form.add(txtTelefono, gbc);

        gbc.gridy++; gbc.gridx = 0;
        form.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        txtEmail = new JTextField(18); form.add(txtEmail, gbc);

        gbc.gridy++; gbc.gridx = 0;
        form.add(new JLabel("Categoría:"), gbc);
        gbc.gridx = 1;
        cmbCategoria = new JComboBox<>(new String[]{"Familia", "Amigos", "Trabajo"});
        form.add(cmbCategoria, gbc);

        gbc.gridy++; gbc.gridx = 0;
        form.add(new JLabel("Favorito:"), gbc);
        gbc.gridx = 1;
        chkFavorito = new JCheckBox();
        form.add(chkFavorito, gbc);

        // Botones (debajo del form)
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2;
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        btnAgregar = new JButton("Agregar");
        btnEditar = new JButton("Editar");
        btnEliminar = new JButton("Eliminar");
        btnExportar = new JButton("Exportar CSV");
        acciones.add(btnAgregar); acciones.add(btnEditar); acciones.add(btnEliminar); acciones.add(btnExportar);
        form.add(acciones, gbc);

        // Tabla central
        tablaContactos = new JTable(new DefaultTableModel(
                new Object[]{"Nombre", "Teléfono", "Email", "Categoría", "Favorito"}, 0
        ));
        tablaContactos.setAutoCreateRowSorter(true);

        panel.add(form, BorderLayout.WEST);
        panel.add(new JScrollPane(tablaContactos), BorderLayout.CENTER);

        return panel;
    }

    private JPanel crearPanelEstadisticas() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        lblTotal = new JLabel("Total de contactos: 0");
        lblCategoria = new JLabel("Categorías: -");
        lblFavoritos = new JLabel("Favoritos: 0");
        p.add(lblTotal);
        p.add(Box.createRigidArea(new Dimension(0,8)));
        p.add(lblCategoria);
        p.add(Box.createRigidArea(new Dimension(0,8)));
        p.add(lblFavoritos);
        return p;
    }

    /**
     * Actualiza etiquetas de estadísticas (vista).
     */
    public void actualizarEstadisticas(java.util.List<persona> contactosList) {
        lblTotal.setText("Total de contactos: " + contactosList.size());
        long favs = contactosList.stream().filter(persona::isFavorito).count();
        lblFavoritos.setText("Favoritos: " + favs);
        Set<String> cats = new LinkedHashSet<>();
        for (persona p : contactosList) cats.add(p.getCategoria());
        lblCategoria.setText("Categorías: " + (cats.isEmpty() ? "-" : String.join(", ", cats)));
    }

    /**
     * Inicializa mapas de traducción.
     */
    private void inicializarIdiomas() {
        Map<String, String> es = new HashMap<>();
        es.put("Contactos", "Contactos"); es.put("Estadísticas", "Estadísticas");
        es.put("Agregar", "Agregar"); es.put("Editar", "Editar"); es.put("Eliminar", "Eliminar");
        es.put("Exportar CSV", "Exportar CSV"); es.put("Favorito", "Favorito");
        es.put("Nombre", "Nombre"); es.put("Teléfono", "Teléfono"); es.put("Email", "Correo");
        es.put("Categorías", "Categorías"); es.put("Total", "Total de contactos"); es.put("Favoritos","Favoritos");
        idiomas.put("Español", es);

        Map<String, String> en = new HashMap<>();
        en.put("Contactos", "Contacts"); en.put("Estadísticas", "Statistics");
        en.put("Agregar", "Add"); en.put("Editar", "Edit"); en.put("Eliminar", "Delete");
        en.put("Exportar CSV", "Export CSV"); en.put("Favorito", "Favorite");
        en.put("Nombre", "Name"); en.put("Teléfono", "Phone"); en.put("Email", "Email");
        en.put("Categorías", "Categories"); en.put("Total", "Total Contacts"); en.put("Favoritos","Favorites");
        idiomas.put("Inglés", en);

        Map<String, String> fr = new HashMap<>();
        fr.put("Contactos", "Contacts"); fr.put("Estadísticas", "Statistiques");
        fr.put("Agregar", "Ajouter"); fr.put("Editar", "Modifier"); fr.put("Eliminar", "Supprimer");
        fr.put("Exportar CSV", "Exporter CSV"); fr.put("Favorito", "Favori");
        fr.put("Nombre", "Nom"); fr.put("Teléfono", "Téléphone"); fr.put("Email", "Courriel");
        fr.put("Categorías", "Catégories"); fr.put("Total", "Contacts totaux"); fr.put("Favoritos","Favoris");
        idiomas.put("Francés", fr);
    }

    /**
     * Cambia textos en tiempo real (llamado por el controlador cuando cambia idioma).
     */
    public void cambiarIdioma(String idioma) {
        Map<String, String> lang = idiomas.get(idioma);
        if (lang == null) return;
        btnAgregar.setText(lang.get("Agregar"));
        btnEditar.setText(lang.get("Editar"));
        btnEliminar.setText(lang.get("Eliminar"));
        btnExportar.setText(lang.get("Exportar CSV"));
        // actualizar etiquetas de estadísticas manteniendo los números existentes:
        String totalVal = lblTotal.getText().replaceAll("^.*:\\s*", "");
        String favVal = lblFavoritos.getText().replaceAll("^.*:\\s*", "");
        String catVal = lblCategoria.getText().replaceAll("^.*:\\s*", "");
        lblTotal.setText(lang.get("Total") + ": " + totalVal);
        lblFavoritos.setText(lang.get("Favoritos") + ": " + favVal);
        lblCategoria.setText(lang.get("Categorías") + ": " + catVal);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ventana::new);
    }
}
