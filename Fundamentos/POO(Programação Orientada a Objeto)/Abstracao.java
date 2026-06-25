abstract class Abstracao {

    public abstract void regra();

    public void logicaComum() {
        System.out.println("Lógica comum da abstração");
    }
}
class AbstracaoImplementacao extends Abstracao {

    @Override
    public void regra() {
        System.out.println("Regra implementada pela classe concreta");
    }
}