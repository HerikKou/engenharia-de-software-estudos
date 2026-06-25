public class Tipos {

    public static void main(String[] args) {

        int idade = 22; // primitivo inteiro, valor direto na stack
        long populacao = 1000000000L; // primitivo long para números grandes
        double salario = 2500.50; // primitivo double para valores decimais
        boolean ativo = true; // primitivo boolean para estados
        char inicial = 'H'; // primitivo char para um único caractere

        String nome = "Herik"; // String texto imutável na heap
        String sobrenome = "Kato"; // outra String na heap

        String nomeCompleto = nome + " " + sobrenome; // concatenação gera nova String

        StringBuilder sb = new StringBuilder(); // objeto mutável para performance

        sb.append("Herik"); // adiciona texto
        sb.append(" "); // adiciona espaço
        sb.append("Kato"); // adiciona sobrenome

        String resultado = sb.toString(); // converte para String final

        Integer idadeWrapper = 22; // wrapper do int, objeto na heap
        Long populacaoWrapper = 1000000000L; // wrapper do long
        Double salarioWrapper = 2500.50; // wrapper do double

        System.out.println("Primitivos:");
        System.out.println(idade);
        System.out.println(populacao);
        System.out.println(salario);
        System.out.println(ativo);
        System.out.println(inicial);

        System.out.println("String:");
        System.out.println(nomeCompleto);

        System.out.println("StringBuilder:");
        System.out.println(resultado);

        System.out.println("Wrappers:");
        System.out.println(idadeWrapper);
        System.out.println(populacaoWrapper);
        System.out.println(salarioWrapper);

        // PRIMITIVOS -> performance e valores simples
        // STRING -> texto fixo e simples
        // STRINGBUILDER -> muitas concatenações (performance)
        // BIGDECIMAL -> dinheiro e precisão alta
        // WRAPPERS -> quando precisa de null ou Collections

       
    }
}