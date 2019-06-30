package ru.hse.lupuleac.lockfree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.stream.Collectors;

public class LockFreeSet<T extends Comparable<T>> implements
        Set<T> {
    private Node head;
    private AtomicInteger counter = new AtomicInteger(0);

    public LockFreeSet() {
        System.err.println("\n\nNew iteration");
        head = new Node(null);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            FindResult findResult = find(value);
            if (findResult.cur != null && findResult.cur.value
                    .compareTo(value) == 0) {
                return false;
            }
            Node node = new Node(value);
            int id = counter.incrementAndGet();
            node.next.set(findResult.cur, id);
            int prevId = findResult.prev.id();
            int newId = counter.incrementAndGet();
            if (prevId >= 0 && findResult.prev.next.compareAndSet(findResult.cur,
                    node, prevId, newId)) {
                /*if (findResult.cur != null && findResult.prev != null) {
                    System.err.println("add " + findResult.prev.value + " " +
                            " " + value + " " + findResult.cur.value);
                } else {
                    if (findResult.cur != null) {
                        System.err.println("add null " + value + " " +
                                findResult.cur.value);
                    }
                    else {
                        if (findResult.prev != null) {
                            System.err.println("add " + findResult.prev.value
                                            + " " + value + " null");
                        } else {
                            System.err.println("add null " + value + " null");
                        }
                    }
                }*/
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
            Node next = nodeToBeRemoved.nextNode();
            if (!nodeToBeRemoved.next.attemptStamp(next, -1)) {
                continue;
            }

            int prevId = findResult.prev.id();
            int newId = counter.incrementAndGet();
            if (prevId >= 0 && findResult.prev.next.compareAndSet
                    (nodeToBeRemoved,
                    next, prevId, newId)) {
                /*if (next != null && findResult.prev != null) {
                    System.err.println("remove " + findResult.prev.value + " " +
                            "" + value +
                            " " + next.value);
                } else {
                    if (next != null) {
                        System.err.println("remove null " + value + " " + next
                                .value);
                    }
                    else {
                        if (findResult.prev != null) {
                            System.err.println("remove " + findResult.prev.value + " " +
                                    value + " null");
                        } else {
                            System.err.println("remove null " + value + " " +
                                    "null");
                        }
                    }
                }*/
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
            if (cur.exists()) {
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
            next = new AtomicStampedReference<>(null, counter.incrementAndGet());
        }

        private T value;
        private AtomicStampedReference<Node> next;

        public boolean exists() {
            return next.getStamp() > 0;
        }

        public Node nextNode() {
            return next.getReference();
        }

        public int id() {
            return next.getStamp();
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
