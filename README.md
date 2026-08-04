# Hardware Connection Tester

Ferramenta simples em **Java puro (sem dependências)** para testar conexões com
hardwares. Sobe dois receptores e loga **tudo** que chega no console (`System.out`),
em texto e em hex dump:

- **HTTP catch-all** — aceita qualquer método (GET, POST, PUT, ...), qualquer rota
  e qualquer `Content-Type` (JSON, XML, texto, binário). Responde `200 OK`.
- **TCP bruto** — aceita sockets puros e loga todos os bytes recebidos, útil para
  dispositivos que não falam HTTP.

## Requisitos

- Java 21+ (usa o *single-file source launch*, então **não precisa compilar**).

## Como rodar

```sh
# Portas padrão: HTTP 8090, TCP 9000
java HardwareTester.java

# Portas customizadas
java HardwareTester.java --http 8090 --tcp 9000
```

> A porta HTTP padrão é 8090 porque a 8080 costuma estar ocupada nesta máquina.

## Como testar

HTTP com JSON:

```sh
curl -X POST "http://localhost:8090/api/dispositivo?serial=ABC123" \
  -H "Content-Type: application/json" \
  -d '{"temp": 25.4, "status": "ok"}'
```

HTTP com XML:

```sh
curl -X PUT "http://localhost:8090/hardware/leitura" \
  -H "Content-Type: application/xml" \
  -d '<leitura><tensao>220</tensao></leitura>'
```

TCP bruto (o `TcpProbe.java` incluído é um cliente de exemplo):

```sh
java TcpProbe.java
```

No Linux/Mac dá pra usar `nc`:

```sh
printf 'PING\r\nSENSOR:42\r\n' | nc localhost 9000
```

No Windows (PowerShell), sem `nc`:

```powershell
$c = New-Object System.Net.Sockets.TcpClient('localhost', 9000)
$s = $c.GetStream()
$b = [Text.Encoding]::UTF8.GetBytes("PING`r`nSENSOR:42`r`n")
$s.Write($b, 0, $b.Length); $c.Close()
```

## Exemplo de saída

```
========================================================================
  HTTP #1
========================================================================
  Quando: 2026-07-15 11:06:19.735
  Origem: /[0:0:0:0:0:0:0:1]:65503
  Método: POST
  URI: /api/dispositivo?serial=ABC123
  Protocolo: HTTP/1.1

  Headers:
    Content-type: application/json
    ...

  Corpo (30 bytes):
    | {"temp": 25.4, "status": "ok"}

    Hex dump:
    00000000  7B 22 74 65 6D 70 22 3A  20 32 35 2E 34 2C 20 22  |{"temp": 25.4, "|
    00000010  73 74 61 74 75 73 22 3A  20 22 6F 6B 22 7D        |status": "ok"}|
========================================================================
```

## Arquivos

- `HardwareTester.java` — a aplicação (HTTP + TCP).
- `TcpProbe.java` — cliente TCP de exemplo para testar o listener bruto.
