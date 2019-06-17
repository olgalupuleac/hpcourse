package ru.hse.lupuleac.lockfree;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.stream.Collectors;

public class LockFreeSet<T extends Comparable<T>> implements
        Set<T> {
    private Node head;
    private AtomicInteger counter = new AtomicInteger(0);

    public LockFreeSet() {
        System.err.println("\nNew iteration");
        head = new Node(null);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            FindResult findResult = find(value);
            if (!findResult.prev.exists()) {
                continue;
            }
            if (findResult.cur != null && findResult.cur.value
                    .compareTo(value) == 0) {
                return false;
            }
            Node node = new Node(value);
            node.next.set(findResult.cur, counter.incrementAndGet());
            int id = counter.incrementAndGet();
            if (findResult.prev.next.compareAndSet(findResult.cur,
                    node, findResult.prev.getId(), id)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            FindResult findResult = find(value);
            Node nodeToBeRemoved = findResult.cur;


            if (nodeToBeRemoved == null || nodeToBeRemoved.value
                    .compareTo(value) != 0) {
                return false;
            }
            System.err.println(Thread.currentThread() + " " + nodeToBeRemoved
                    .value
                    + " " + value);
            Node next = nodeToBeRemoved.nextNode();
            if (!nodeToBeRemoved.next.attemptStamp(next, -1)) {
                continue;
            }
            int id = counter.incrementAndGet();
            if (findResult.prev.next.compareAndSet(nodeToBeRemoved,
                    next, findResult.prev.getId(), id)) {
                System.err.println(Thread.currentThread() + " true");
                return true;
            }
        }
    }


    private FindResult find(T key) {
        Node prev = head;
        Node cur = head.nextNode();
        while (cur != null && cur.value.compareTo(key) <
                0) {
            prev = cur;
            cur = cur.nextNode();
        }
        return new FindResult(prev, cur);
    }

    @Override
    public boolean contains(T value) {
        Node cur = head.nextNode();
        while (cur != null && (cur.value.compareTo
                        (value) < 0)) {
            cur = cur.nextNode();
        }
        return cur != null && cur.value.compareTo(value) == 0 && cur
                .exists();
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    private List<Node> getNodes() {
        Node cur = head.nextNode();
        List<Node> res = new ArrayList<>();
        while (cur != null) {
           if (cur.exists()){
               res.add(cur);
           }
           cur = cur.nextNode();
        }
        return res;
    }

    public List<T> scan() {
        List<Node> snapshot = getNodes();
        while (true) {
            List<Node> secondSnapshot = getNodes();

            if (snapshot.equals(secondSnapshot)) {
                return secondSnapshot.stream()
                        .map(t -> t.value)
                        .collect(
                                Collectors.toList()
                        );
            }
            snapshot = secondSnapshot;
        }
    }


    @Override
    public Iterator<T> iterator() {
        return scan().iterator();
    }


    private class Node {
        private Node(T value) {
            this.value = value;
            int id = LockFreeSet.this.counter.incrementAndGet();
            next = new AtomicStampedReference<>(null, id);
        }

        private T value;
        private AtomicStampedReference<Node> next;

        public boolean exists() {
            return next.getStamp() >= 0;
        }

        public int getId() {
            return next.getStamp();
        }

        public Node nextNode() {
            return next.getReference();
        }

        @Override
        public boolean equals(Object obj) {
            return this.getClass().isInstance(obj) && ((Node)obj).getId() == getId();
        }
    }

    private class FindResult {
        private Node prev;
        private Node cur;

        private FindResult(Node prev, Node cur) {
            this.prev = prev;
            this.cur = cur;
        }
    }
}
