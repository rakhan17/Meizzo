# V2 migration

V2 migration is incremental. Package IDs, bundle IDs, pairing semantics, and
working beta regression tests are preserved.

1. Add protocol version/capability negotiation.
2. Extract Android transport and identity modules without changing wire
   behavior.
3. Introduce state-driven presentation and a shared Android contract module.
4. Add durable backend identity and session persistence.
5. Add session-scoped consent before media or sensitive capabilities.
6. Publish only after production WSS, stable signing, and runtime validation
   are available.

Beta.3 signing compatibility is not established in the current environment;
therefore no migration release should promise upgrade installation over that
artifact until the historical certificate is verified.
