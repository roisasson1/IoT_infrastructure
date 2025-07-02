package gateway.RPS.parser;

public interface Parsable<D, T> {
    T parse(D input);
}
