package controlador;

import modelo.persona;
import modelo.personaDAO;
import vista.ventana;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador (MVC). Registra eventos, sincroniza modelo y vista,
 * y usa personaDAO para persistencia.
 */
public class logica_ventana {

    private final ventana vista;
    private final List<persona> contactos = new ArrayList<>();
    private final personaDAO dao = new personaDAO();
    private boolean modoEdicion = false;
    private int filaEdicion = -1;

    public logica_ventana(ventana v) {
        this.vista = v;
        cargarContactosDesdeDAO();
        registrarEventos();
        actualizarTabla(); // muestra datos cargados
        vista.actualizarEstadisticas(contactos);
        agregarMenuContextualTabla();
        agregarAtajosTeclado();
    }

    private void cargarContactosDesdeDAO() {
        try {
            contactos.addAll(dao.cargarContactos());
        } catch (IOException e) {
            // No interrumpe la ejecución, solo informa en consola
            System.err.println("No se pudieron cargar contactos: " + e.getMessage());
        }
    }

    private void registrarEventos() {
        vista.btnAgregar.addActionListener(e -> manejarAgregarGuardar());
        vista.btnEliminar.addActionListener(e -> eliminarContacto());
        vista.btnExportar.addActionListener(e -> exportarCSV());
        vista.btnEditar.addActionListener(e -> prepararEdicion());
        vista.cmbIdioma.addActionListener(e -> vista.cambiarIdioma((String) vista.cmbIdioma.getSelectedItem()));

        // Enter en campo email también agrega/guarda
        vista.txtEmail.addActionListener(e -> manejarAgregarGuardar());

        // Doble clic en tabla: cargar para editar
        vista.tablaContactos.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
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
     * Agrega o guarda edición según el modo.
     */
    private void manejarAgregarGuardar() {
        if (modoEdicion) {
            guardarEdicion();
        } else {
            agregarContacto();
        }
    }

    /**
     * Validaciones simples y creación de contacto.
     */
    private void agregarContacto() {
        try {
            validarCamposObligatorios();
            persona p = new persona(
                    vista.txtNombre.getText().trim(),
                    vista.txtTelefono.getText().trim(),
                    vista.txtEmail.getText().trim(),
                    (String) vista.cmbCategoria.getSelectedItem(),
                    vista.chkFavorito.isSelected()
            );
            contactos.add(p);
            actualizarTabla();
            vista.actualizarEstadisticas(contactos);
            dao.guardarContactos(contactos); // persistir cambios
            limpiarCampos();
        } catch (IllegalArgumentException iae) {
            JOptionPane.showMessageDialog(vista, iae.getMessage(), "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(vista, "No se pudo guardar en archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE);
        }
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
     * Guarda cambios de edición en la lista y en la tabla.
     */
    private void guardarEdicion() {
        try {
            validarCamposObligatorios();
            if (filaEdicion < 0 || filaEdicion >= contactos.size()) {
                JOptionPane.showMessageDialog(vista, "Fila de edición inválida.", "Error", JOptionPane.ERROR_MESSAGE);
                modoEdicion = false;
                vista.btnAgregar.setEnabled(true);
                vista.btnEditar.setText("Editar");
                return;
            }
            persona p = contactos.get(filaEdicion);
            p.setNombre(vista.txtNombre.getText().trim());
            p.setTelefono(vista.txtTelefono.getText().trim());
            p.setEmail(vista.txtEmail.getText().trim());
            p.setCategoria((String) vista.cmbCategoria.getSelectedItem());
            p.setFavorito(vista.chkFavorito.isSelected());
            actualizarTabla();
            vista.actualizarEstadisticas(contactos);
            dao.guardarContactos(contactos); // persistir
            limpiarCampos();
            modoEdicion = false;
            filaEdicion = -1;
            vista.btnAgregar.setEnabled(true);
            vista.btnEditar.setText("Editar");
            JOptionPane.showMessageDialog(vista, "Contacto actualizado correctamente.");
        } catch (IllegalArgumentException iae) {
            JOptionPane.showMessageDialog(vista, iae.getMessage(), "Validación", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(vista, "No se pudo guardar en archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE);
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
            contactos.remove(fila);
            actualizarTabla();
            vista.actualizarEstadisticas(contactos);
            try {
                dao.guardarContactos(contactos);
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(vista, "No se pudo actualizar archivo: " + ioe.getMessage(), "Error IO", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Exportar a CSV (llama al DAO).
     */
    private void exportarCSV() {
        try {
            dao.guardarContactos(contactos);
            JOptionPane.showMessageDialog(vista, "Contactos guardados correctamente en datosContactos.csv");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(vista, "Error al exportar: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Valida que los campos obligatorios estén llenos; lanza IllegalArgumentException si falla.
     */
    private void validarCamposObligatorios() {
        String nombre = vista.txtNombre.getText().trim();
        String telefono = vista.txtTelefono.getText().trim();
        String email = vista.txtEmail.getText().trim();
        if (nombre.isEmpty() || telefono.isEmpty() || email.isEmpty()) {
            throw new IllegalArgumentException("Nombre, teléfono y email son obligatorios.");
        }
        // Validación básica de email (muy simple)
        if (!email.contains("@") || email.length() < 5) {
            throw new IllegalArgumentException("Ingrese un email válido.");
        }
    }

    /**
     * Actualiza la tabla visual desde la lista 'contactos'.
     */
    private void actualizarTabla() {
        DefaultTableModel modelo = (DefaultTableModel) vista.tablaContactos.getModel();
        modelo.setRowCount(0);
        for (persona p : contactos) {
            modelo.addRow(new Object[]{p.getNombre(), p.getTelefono(), p.getEmail(), p.getCategoria(), p.isFavorito()});
        }
    }

    /**
     * Carga campos del formulario desde la fila indicada.
     */
    private void cargarCamposDesdeFila(int fila) {
        if (fila < 0 || fila >= contactos.size()) return;
        persona p = contactos.get(fila);
        vista.txtNombre.setText(p.getNombre());
        vista.txtTelefono.setText(p.getTelefono());
        vista.txtEmail.setText(p.getEmail());
        vista.cmbCategoria.setSelectedItem(p.getCategoria());
        vista.chkFavorito.setSelected(p.isFavorito());
        // Preparar modo edición si usuario desea editar luego
    }

    /**
     * Limpia los campos del formulario.
     */
    private void limpiarCampos() {
        vista.txtNombre.setText("");
        vista.txtTelefono.setText("");
        vista.txtEmail.setText("");
        vista.cmbCategoria.setSelectedIndex(0);
        vista.chkFavorito.setSelected(false);
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
     * Agrega atajos de teclado: Enter guarda/agrega; Delete borra seleccionado.
     */
    private void agregarAtajosTeclado() {
        // Enter ya está ligado al action listener del campo email
        // Delete para eliminar fila seleccionada
        vista.tablaContactos.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "eliminar");
        vista.tablaContactos.getActionMap().put("eliminar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                eliminarContacto();
            }
        });

        // Ctrl+S para exportar
        vista.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "exportar");
        vista.getRootPane().getActionMap().put("exportar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportarCSV();
            }
        });
    }
}
