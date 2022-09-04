# Orkes Conductor
Orkes Conductor is a fully compatible version of Netflix Conductor with Orkes certified stack.

<pre>
______   .______       __  ___  _______     _______.     ______   ______   .__   __.  _______   __    __    ______ .___________.  ______   .______
/  __  \  |   _  \     |  |/  / |   ____|   /       |    /      | /  __  \  |  \ |  | |       \ |  |  |  |  /      ||           | /  __  \  |   _  \
|  |  |  | |  |_)  |    |  '  /  |  |__     |   (----`   |  ,----'|  |  |  | |   \|  | |  .--.  ||  |  |  | |  ,----'`---|  |----`|  |  |  | |  |_)  |
|  |  |  | |      /     |    <   |   __|     \   \       |  |     |  |  |  | |  . `  | |  |  |  ||  |  |  | |  |         |  |     |  |  |  | |      /
|  `--'  | |  |\  \----.|  .  \  |  |____.----)   |      |  `----.|  `--'  | |  |\   | |  '--'  ||  `--'  | |  `----.    |  |     |  `--'  | |  |\  \----.
\______/  | _| `._____||__|\__\ |_______|_______/        \______| \______/  |__| \__| |_______/  \______/   \______|    |__|      \______/  | _| `._____|
</pre>

### Stack
1. Redis as a the primary store for running workflows
2. Postgres for storing completed workflows and indexing enabling full text search
3. Redis based queues that improve upon dyno-queues and providers higher performance and are built from ground up to support Redis standalone and cluster mode

### License
Orkes Conductor is fully open source and is available under Orkes Community License

**TL;DR**
Orkes Community License allows anyone to download, redistribute and use Orkes Conductor version in production
