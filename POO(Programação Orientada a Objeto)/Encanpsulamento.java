public class Encanpsulamento {
   private String nome; // Atributo privado
   private int idade; // Atributo privado
   // Getter para acessar o nome
   public String getNome() {
       return nome;
   }
   // Setter para modificar o nome
   public void setNome(String nome) {
       this.nome = nome;
   }
   // Getter para acessar a idade
   public int getIdade() {
       return idade;
   }
   // Setter para modificar a idade com validação
   public void setIdade(int idade) {
       if (idade > 0) { // Validação para garantir consistência
           this.idade = idade;
       }
   }
}