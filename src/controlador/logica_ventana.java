package controlador;

import modelo.persona;
import modelo.personaDAO;
import vista.ventana;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * - Validación de duplicados en segundo plano (SwingWorker)
 * - Búsqueda en segundo plano (SwingWorker)
 * - Exportación con JFileChooser en background (ExecutorService)
 * - Protección de la lista contactos con ReentrantLock
 */
public class logica_ventana {

    private final ventana vista;
    private final List<persona> contactos = new ArrayList<>();
    private final personaDAO dao = new personaDAO();
    private boolean modoEdicion = false;
    private int filaEdicion = -1;

    // Lock para proteger la lista 'contactos'
    private final ReentrantLock lockContactos = new ReentrantLock(true);

    // Executor para tareas en background (export, persistencia)
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "background-worker");
        t.setDaemon(true);
        return t;
    });

    public logica_ventana(ventana v) {
        this.vista = v;

        // Carga inicial de contactos
        cargarContactosDesdeDAO();

        // Registro de eventos en la vista
        registrarEventos();

        // Si la vista expone txtBuscar, agrega el DocumentListener (búsqueda en background)
        if (vista.txtBuscar != null) {
            vista.txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { buscarContactosEnSegundoPlano(vista.txtBuscar.getText()); }
                @Override public void removeUpdate(DocumentEvent e) { buscarContactosEnSegundoPlano(vista.txtBuscar.getText()); }
                @Override public void changedUpdate(DocumentEvent e) { buscarContactosEnSegundoPlano(vista.txtBuscar.getText()); }
            });
        }

        // Mostrar los contactos cargados
        actualizarTabla();
        vista.actualizarEstadisticas(contactos);

        // Agregar menú contextual y atajos
        agregarMenuContextualTabla();
        agregarAtajosTeclado();
    }

    private void cargarContactosDesdeDAO() {
        try {
            List<persona> cargados = dao.cargarContactos();
            lockContactos.lock();
            try {
                contactos.addAll(cargados);
            } finally {
                lockContactos.unlock();
            }
        } catch (IOException e) {
            System.err.println("No se pudieron cargar contactos: " + e.getMessage());
        }
    }

    private void registrarEventos() {
        vista.btnAgregar.addActionListener(e -> manejarAgregarGuardar());
        vista.btnEliminar.addActionListener(e -> eliminarContacto());
        vista.btnExportar.addActionListener(e -> exportarCSVBackgroundWithChooser());
        vista.btnEditar.addActionListener(e -> prepararEdicion());
        vista.cmbIdioma.addActionListener(e -> vista.cambiarIdioma((String) vista.cmbIdioma.getSelectedItem()));

        // Enter en campo email también agrega/guarda
        vista.txtEmail.addActionListener(e -> manejarAgregarGuardar());

        // Doble clic en tabla: cargar para editar
        vista.tablaContactos.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int fila = vista.tablaContactos.getSelectedRow();
                    if (fila != -1) {
                        cargarCamposDesdeFila(fila);
                    }
                }
            }
        });
    }

    /**
     * Agregar o guardar edición según el modo.
     * La validación de duplicados se realiza en segundo plano.
     */
    private void manejarAgregarGuardar() {
        if (modoEdicion) {
            guardarEdicion();
        } else {
            agregarContactoBackground();
        }
    }

    /**
     * Agrega contacto validando duplicados en segundo plano con SwingWorker.
     */
    private void agregarContactoBackground() {
        final String nombre = vista.txtNombre.getText().trim();
        final String telefono = vista.txtTelefono.getText().trim();
        final String email = vista.txtEmail.getText().trim();
        final String categoria = (String) vista.cmbCategoria.getSelectedItem();
        final boolean favorito = vista.chkFavorito.isSelected();

        // validaciones rápidas en UI thread (para feedback inmediato)
        try {
            validarCamposObligatorios(nombre, telefono, email);
        } catch (IllegalArgumentException iae) {
            JOptionPane.showMessageDialog(vista, iae.getMessage(), "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // SwingWorker: buscar duplicados sin bloquear UI
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private boolean existe = false;
            private String motivo = "";

            @Override
            protected Boolean doInBackground() {
                lockContactos.lock();
                try {
                    for (persona p : contactos) {
                        if (p.getNombre().equalsIgnoreCase(nombre)) {
                            existe = true;
                            motivo = "nombre";
                            break;
                        }
                        if (p.getTelefono().equalsIgnoreCase(telefono)) {
                            existe = true;
                            motivo = "telefono";
                            break;
                        }
                    }
                } finally {
                    lockContactos.unlock();
                }
                return existe;
            }

            @Override
            protected void done() {
                try {
                    boolean duplicado = get();
                    if (duplicado) {
                        String msg = "Ya existe un contacto con ese " + ("telefono".equals(motivo) ? "teléfono." : "nombre.");
                        JOptionPane.showMessageDialog(vista, msg, "Duplicado", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    // No existe: agregamos al listado (protegido) y persistimos (en hilo separado)
                    persona nuevo = new persona(nombre, telefono, email, categoria, favorito);
                    lockContactos.lock();
                    try {
                        contactos.add(nuevo);
                    } finally {
                        lockContactos.unlock();
                    }
                    // Actualizar UI
                    actualizarTabla();
                    vista.actualizarEstadisticas(contactos);

                    // Guardar persistencia en background (usando executor single-thread)
                    executor.submit(() -> {
                        try {
                            dao.guardarContactos(contactos);
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(vista, "Contacto agregado correctamente"));
                        } catch (IOException ioe) {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(vista, "No se pudo guardar en archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE));
                        }
                    });

                    limpiarCampos();
                } catch (InterruptedException | ExecutionException ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "Error en la validación: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }
        };
        worker.execute();
    }

    /**
     * Prepara la UI para edición (carga datos en campos y cambia estado).
     */
    private void prepararEdicion() {
        int fila = vista.tablaContactos.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(vista, "Seleccione un contacto para editar.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        cargarCamposDesdeFila(fila);
        modoEdicion = true;
        filaEdicion = fila;
        vista.btnAgregar.setEnabled(false);
        vista.btnEditar.setText("Guardar");
    }

    /**
     * Guarda cambios de edición; persistencia se realiza en background.
     */
    private void guardarEdicion() {
        try {
            String nombre = vista.txtNombre.getText().trim();
            String telefono = vista.txtTelefono.getText().trim();
            String email = vista.txtEmail.getText().trim();
            validarCamposObligatorios(nombre, telefono, email);

            lockContactos.lock();
            try {
                if (filaEdicion < 0 || filaEdicion >= contactos.size()) {
                    JOptionPane.showMessageDialog(vista, "Fila de edición inválida.", "Error", JOptionPane.ERROR_MESSAGE);
                    modoEdicion = false;
                    vista.btnAgregar.setEnabled(true);
                    vista.btnEditar.setText("Editar");
                    return;
                }
                persona p = contactos.get(filaEdicion);
                p.setNombre(nombre);
                p.setTelefono(telefono);
                p.setEmail(email);
                p.setCategoria((String) vista.cmbCategoria.getSelectedItem());
                p.setFavorito(vista.chkFavorito.isSelected());
            } finally {
                lockContactos.unlock();
            }

            actualizarTabla();
            vista.actualizarEstadisticas(contactos);

            executor.submit(() -> {
                try {
                    dao.guardarContactos(contactos);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "Contacto actualizado correctamente."));
                } catch (IOException ioe) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "No se pudo guardar en archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE));
                }
            });

            limpiarCampos();
            modoEdicion = false;
            filaEdicion = -1;
            vista.btnAgregar.setEnabled(true);
            vista.btnEditar.setText("Editar");
        } catch (IllegalArgumentException iae) {
            JOptionPane.showMessageDialog(vista, iae.getMessage(), "Validación", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Elimina contacto seleccionado tras confirmación.
     */
    private void eliminarContacto() {
        int fila = vista.tablaContactos.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(vista, "Seleccione un contacto para eliminar.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(vista, "¿Desea eliminar este contacto?", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            lockContactos.lock();
            try {
                if (fila >= 0 && fila < contactos.size()) contactos.remove(fila);
            } finally {
                lockContactos.unlock();
            }
            actualizarTabla();
            vista.actualizarEstadisticas(contactos);

            executor.submit(() -> {
                try {
                    dao.guardarContactos(contactos);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "Contacto eliminado correctamente."));
                } catch (IOException ioe) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "No se pudo actualizar archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE));
                }
            });
        }
    }

    /**
     * Exportar a CSV usando JFileChooser (el usuario elige la ruta). Exportación segura en background.
     */
    private void exportarCSVBackgroundWithChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar archivo CSV");
        chooser.setSelectedFile(new File("contactos_exportados.csv"));

        int result = chooser.showSaveDialog(vista);
        if (result != JFileChooser.APPROVE_OPTION) {
            return; // el usuario canceló
        }

        File archivo = chooser.getSelectedFile();

        vista.btnExportar.setEnabled(false);
        executor.submit(() -> {
            try {
                List<persona> snapshot;
                lockContactos.lock();
                try {
                    snapshot = new ArrayList<>(contactos);
                } finally {
                    lockContactos.unlock();
                }

                synchronized (personaDAO.class) {
                    try (FileWriter fw = new FileWriter(archivo)) {
                        fw.write("NOMBRE;TELEFONO;EMAIL;CATEGORIA;FAVORITO\n");
                        for (persona p : snapshot) {
                            fw.write(p.toString());
                            fw.write("\n");
                        }
                        fw.flush();
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    vista.btnExportar.setEnabled(true);
                    JOptionPane.showMessageDialog(vista, "Exportación completada: " + archivo.getAbsolutePath());
                });
            } catch (IOException ioe) {
                SwingUtilities.invokeLater(() -> {
                    vista.btnExportar.setEnabled(true);
                    JOptionPane.showMessageDialog(vista, "Error al exportar: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /**
     * Búsqueda en segundo plano: filtra contactos por nombre/telefono/email parcialmente.
     */
    public void buscarContactosEnSegundoPlano(String query) {
        final String q = query == null ? "" : query.trim().toLowerCase();
        SwingWorker<List<persona>, Void> searcher = new SwingWorker<>() {
            @Override
            protected List<persona> doInBackground() {
                List<persona> resultado = new ArrayList<>();
                lockContactos.lock();
                try {
                    for (persona p : contactos) {
                        if (q.isEmpty() ||
                                p.getNombre().toLowerCase().contains(q) ||
                                p.getTelefono().toLowerCase().contains(q) ||
                                p.getEmail().toLowerCase().contains(q)) {
                            resultado.add(p);
                        }
                    }
                } finally {
                    lockContactos.unlock();
                }
                return resultado;
            }

            @Override
            protected void done() {
                try {
                    List<persona> res = get();
                    DefaultTableModel modelo = (DefaultTableModel) vista.tablaContactos.getModel();
                    modelo.setRowCount(0);
                    for (persona p : res) {
                        modelo.addRow(new Object[]{p.getNombre(), p.getTelefono(), p.getEmail(), p.getCategoria(), p.isFavorito()});
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(vista, "Error en búsqueda: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }
        };
        searcher.execute();
    }

    /**
     * Valida que los campos obligatorios estén llenos; lanza IllegalArgumentException si falla.
     */
    private void validarCamposObligatorios(String nombre, String telefono, String email) {
        if (nombre.isEmpty() || telefono.isEmpty() || email.isEmpty()) {
            throw new IllegalArgumentException("Nombre, teléfono y email son obligatorios.");
        }
        if (!email.contains("@") || email.length() < 5) {
            throw new IllegalArgumentException("Ingrese un email válido.");
        }
    }

    /**
     * Actualiza la tabla visual desde la lista 'contactos' en EDT.
     */
    private void actualizarTabla() {
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel modelo = (DefaultTableModel) vista.tablaContactos.getModel();
            modelo.setRowCount(0);
            lockContactos.lock();
            try {
                for (persona p : contactos) {
                    modelo.addRow(new Object[]{p.getNombre(), p.getTelefono(), p.getEmail(), p.getCategoria(), p.isFavorito()});
                }
            } finally {
                lockContactos.unlock();
            }
        });
    }

    /**
     * Carga campos del formulario desde la fila indicada.
     */
    private void cargarCamposDesdeFila(int fila) {
        lockContactos.lock();
        try {
            if (fila < 0 || fila >= contactos.size()) return;
            persona p = contactos.get(fila);
            vista.txtNombre.setText(p.getNombre());
            vista.txtTelefono.setText(p.getTelefono());
            vista.txtEmail.setText(p.getEmail());
            vista.cmbCategoria.setSelectedItem(p.getCategoria());
            vista.chkFavorito.setSelected(p.isFavorito());
        } finally {
            lockContactos.unlock();
        }
    }

    /**
     * Limpia los campos del formulario en EDT.
     */
    private void limpiarCampos() {
        SwingUtilities.invokeLater(() -> {
            vista.txtNombre.setText("");
            vista.txtTelefono.setText("");
            vista.txtEmail.setText("");
            vista.cmbCategoria.setSelectedIndex(0);
            vista.chkFavorito.setSelected(false);
        });
    }

    /**
     * Menú contextual (clic derecho) en la tabla: Editar / Eliminar.
     */
    private void agregarMenuContextualTabla() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem miEditar = new JMenuItem("Editar");
        JMenuItem miEliminar = new JMenuItem("Eliminar");
        popup.add(miEditar);
        popup.add(miEliminar);

        miEditar.addActionListener(e -> {
            int fila = vista.tablaContactos.getSelectedRow();
            if (fila != -1) {
                cargarCamposDesdeFila(fila);
                modoEdicion = true;
                filaEdicion = fila;
                vista.btnAgregar.setEnabled(false);
                vista.btnEditar.setText("Guardar");
            }
        });

        miEliminar.addActionListener(e -> eliminarContacto());

        vista.tablaContactos.setComponentPopupMenu(popup);
    }

    /**
     * Agrega atajos de teclado: Enter guarda/agrega; Delete borra seleccionado; Ctrl+S para exportar.
     */
    private void agregarAtajosTeclado() {
        // Delete para eliminar fila seleccionada
        vista.tablaContactos.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "eliminar");
        vista.tablaContactos.getActionMap().put("eliminar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                eliminarContacto();
            }
        });

        // Ctrl+S para exportar (usa executor)
        vista.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "exportar");
        vista.getRootPane().getActionMap().put("exportar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportarCSVBackgroundWithChooser();
            }
        });
    }

    /**
     * Llamar en cierre de la app si se desea parar el executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}