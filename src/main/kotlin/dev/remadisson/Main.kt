package dev.remadisson.de.remadisson


private val ipv4Checks: Map<String, Ipv4Check> = HashMap();
fun main(args: Array<String>) {
    val zoneID: String = args[0];
    if(zoneID.isBlank()){

        return;
    }
    for(arg in args.copyOfRange(1, args.size)){
        println(arg)
    }
}