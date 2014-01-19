package com.tinkerpop.gremlin;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.query.util.GraphQueryBuilder;
import com.tinkerpop.gremlin.pipes.map.GraphQueryPipe;
import com.tinkerpop.gremlin.pipes.map.IdentityPipe;
import com.tinkerpop.gremlin.pipes.util.HolderIterator;
import com.tinkerpop.gremlin.pipes.util.optimizers.HolderOptimizer;
import com.tinkerpop.gremlin.pipes.util.optimizers.IdentityOptimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Gremlin<S, E> implements Pipeline<S, E> {

    private final Map<String, Object> variables = new HashMap<>();
    private final List<Pipe<?, ?>> pipes = new ArrayList<>();
    private final List<Optimizer> optimizers = new ArrayList<>();
    private Graph graph = null;
    private boolean firstNext = true;

    private Gremlin(final Graph graph) {
        this.graph = graph;
        this.optimizers.add(new IdentityOptimizer());
        this.optimizers.add(new HolderOptimizer());
        //this.optimizers.add(new VertexQueryOptimizer());
    }

    private Gremlin(final Iterator<S> starts) {
        final Pipe<S, S> pipe = new IdentityPipe<>(this);
        this.addPipe(pipe);
        this.addStarts(new HolderIterator<>(Optional.empty(), pipe, starts, false));
    }

    public static Gremlin<?, ?> of() {
        return new Gremlin<>(Collections.emptyIterator());
    }

    public static Gremlin<?, ?> of(final Graph graph) {
        return new Gremlin(graph);
    }

    public <T> void put(final String variable, final T t) {
        this.variables.put(variable, t);
    }

    public <T> Optional<T> get(final String variable) {
        return Optional.ofNullable((T) this.variables.get(variable));
    }

    public void registerOptimizer(final Optimizer optimizer) {
        this.optimizers.add(optimizer);
    }

    public Gremlin<Vertex, Vertex> V() {
        Objects.requireNonNull(this.graph);
        GraphQueryPipe pipe = new GraphQueryPipe<>(this, this.graph, new GraphQueryBuilder(), Vertex.class);
        this.addPipe(pipe);
        return (Gremlin<Vertex, Vertex>) this;
    }

    public Gremlin<Edge, Edge> E() {
        Objects.requireNonNull(this.graph);
        GraphQueryPipe pipe = new GraphQueryPipe<>(this, this.graph, new GraphQueryBuilder(), Edge.class);
        this.addPipe(pipe);
        return (Gremlin<Edge, Edge>) this;
    }

    public Gremlin<Vertex, Vertex> v(final Object... ids) {
        Objects.requireNonNull(this.graph);
        GraphQueryPipe pipe = new GraphQueryPipe<>(this, this.graph, new GraphQueryBuilder().ids(ids), Vertex.class);
        this.addPipe(pipe);
        return (Gremlin<Vertex, Vertex>) this;
    }

    public Gremlin<Edge, Edge> e(final Object... ids) {
        Objects.requireNonNull(this.graph);
        GraphQueryPipe pipe = new GraphQueryPipe<>(this, this.graph, new GraphQueryBuilder().ids(ids), Edge.class);
        this.addPipe(pipe);
        return (Gremlin<Edge, Edge>) this;
    }

    public void addStarts(final Iterator<Holder<S>> starts) {
        ((Pipe<S, ?>) this.pipes.get(0)).addStarts(starts);
    }

    public List<Pipe<?, ?>> getPipes() {
        return this.pipes;
    }

    public <P extends Pipeline> P addPipe(final Pipe pipe) {
        if (this.pipes.size() > 0)
            pipe.addStarts(this.pipes.get(this.pipes.size() - 1));
        this.pipes.add(pipe);
        this.optimize(Optimizer.Rate.STEP_COMPILE_TIME);
        return (P) this;
    }

    public boolean hasNext() {
        this.optimize(Optimizer.Rate.FINAL_COMPILE_TIME);
        return this.pipes.get(this.pipes.size() - 1).hasNext();
    }

    public E next() {
        this.optimize(Optimizer.Rate.FINAL_COMPILE_TIME);
        return (E) this.pipes.get(this.pipes.size() - 1).next().get();
    }

    public String toString() {
        return this.getPipes().toString();
    }

    private void optimize(final Optimizer.Rate rate) {
        if (rate.equals(Optimizer.Rate.FINAL_COMPILE_TIME)) {
            if (this.firstNext)
                this.firstNext = false;
            else
                return;
        }
        this.optimizers.stream().
                filter(o -> o.getOptimizationRate().equals(rate))
                .forEach(o -> o.optimize(this));
    }

}
