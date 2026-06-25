class Polimorfismo {

    public void executar() {
        System.out.println("Comportamento padrão");
    }
}

class PolimorfismoA extends Polimorfismo {

    @Override
    public void executar() {
        System.out.println("Comportamento da classe A");
    }
}
class PolimorfismoB extends Polimorfismo {

    @Override
    public void executar() {
        System.out.println("Comportamento da classe B");
    }
}