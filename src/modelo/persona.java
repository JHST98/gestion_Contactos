package modelo;

/**
 * Modelo: representa un contacto.
 */
public class persona {
    private String nombre;
    private String telefono;
    private String email;
    private String categoria;
    private boolean favorito;

    public persona() {
        this("", "", "", "", false);
    }

    public persona(String nombre, String telefono, String email, String categoria, boolean favorito) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.email = email;
        this.categoria = categoria;
        this.favorito = favorito;
    }

    // Getters y setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public boolean isFavorito() { return favorito; }
    public void setFavorito(boolean favorito) { this.favorito = favorito; }

    /**
     * Línea CSV para persistencia (separador ';').
     */
    @Override
    public String toString() {
        return String.format("%s;%s;%s;%s;%s", nombre, telefono, email, categoria, favorito);
    }
}
