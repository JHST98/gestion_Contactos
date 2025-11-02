package modelo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO: lectura y escritura simple en CSV (datosContactos.csv).
 * Usa ';' como separador para evitar conflicto con comas.
 */
public class personaDAO {

    private final File archivo;

    public personaDAO() {
        this.archivo = new File("datosContactos.csv");
    }

    /**
     * Guarda la lista completa de contactos (reescribe el archivo).
     */
    public void guardarContactos(List<persona> contactos) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo))) {
            for (persona p : contactos) {
                bw.write(p.toString());
                bw.newLine();
            }
            bw.flush();
        }
    }

    /**
     * Carga contactos desde archivo; si no existe, devuelve lista vacía.
     */
    public List<persona> cargarContactos() throws IOException {
        List<persona> lista = new ArrayList<>();
        if (!archivo.exists()) return lista;

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] s = linea.split(";");
                if (s.length >= 5) {
                    String nombre = s[0];
                    String telefono = s[1];
                    String email = s[2];
                    String categoria = s[3];
                    boolean favorito = Boolean.parseBoolean(s[4]);
                    lista.add(new persona(nombre, telefono, email, categoria, favorito));
                }
            }
        }
        return lista;
    }

    /**
     * Elimina el archivo de persistencia (si existe).
     */
    public boolean eliminarArchivo() {
        if (archivo.exists()) {
            return archivo.delete();
        }
        return false;
    }
}
