class Animal {
    String nome;

    public void comer() {
        System.out.println("O animal está comendo");
    }
}

class Cachorro extends Animal {

    public void latir() {
        System.out.println("O cachorro está latindo");
    }
}